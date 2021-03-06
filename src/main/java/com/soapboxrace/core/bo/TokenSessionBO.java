package com.soapboxrace.core.bo;

import com.google.common.hash.Hashing;
import com.soapboxrace.core.api.util.GeoIp2;
import com.soapboxrace.core.api.util.UUIDGen;
import com.soapboxrace.core.bo.util.PwnedPasswords;
import com.soapboxrace.core.dao.TokenSessionDAO;
import com.soapboxrace.core.dao.UserDAO;
import com.soapboxrace.core.jpa.BanEntity;
import com.soapboxrace.core.jpa.PersonaEntity;
import com.soapboxrace.core.jpa.TokenSessionEntity;
import com.soapboxrace.core.jpa.UserEntity;
import com.soapboxrace.jaxb.*;
import com.soapboxrace.jaxb.login.LoginStatusVO;
import com.soapboxrace.core.bo.HardwareInfoBO;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Base64;
import java.util.Date;

@Stateless
public class TokenSessionBO {
	@EJB
	private TokenSessionDAO tokenDAO;

	@EJB
	private UserDAO userDAO;

	@EJB
	private ParameterBO parameterBO;

	@EJB
	private GetServerInformationBO serverInfoBO;

	@EJB
	private AuthenticationBO authenticationBO;

	@EJB
	private Argon2BO argon2;

	@EJB
	private HardwareInfoBO hardwareInfoBO;

	public boolean verifyToken(Long userId, String securityToken) {
		TokenSessionEntity tokenSessionEntity = tokenDAO.findById(securityToken);
		if (tokenSessionEntity == null || !tokenSessionEntity.getUserId().equals(userId)) {
			return false;
		}
		long time = new Date().getTime();
		long tokenTime = tokenSessionEntity.getExpirationDate().getTime();
		if (time > tokenTime) {
			return false;
		}
		tokenSessionEntity.setExpirationDate(getMinutes(3));
		tokenDAO.update(tokenSessionEntity);
		return true;
	}

	public void updateToken(String securityToken) {
		TokenSessionEntity tokenSessionEntity = tokenDAO.findById(securityToken);
		Date expirationDate = getMinutes(3);
		tokenSessionEntity.setExpirationDate(expirationDate);
		tokenDAO.update(tokenSessionEntity);
	}

	public String createToken(Long userId, String clientHostName) {
		TokenSessionEntity tokenSessionEntity = new TokenSessionEntity();
		Date expirationDate = getMinutes(15);
		tokenSessionEntity.setExpirationDate(expirationDate);
		String randomUUID = UUIDGen.getRandomUUID();
		tokenSessionEntity.setSecurityToken(randomUUID);
		tokenSessionEntity.setUserId(userId);
		UserEntity userEntity = userDAO.findById(userId);
		tokenSessionEntity.setPremium(userEntity.isPremium());
		tokenSessionEntity.setClientHostIp(clientHostName);
		tokenSessionEntity.setActivePersonaId(0L);
		tokenDAO.insert(tokenSessionEntity);
		return randomUUID;
	}

	public boolean verifyPersona(String securityToken, Long personaId) {
		TokenSessionEntity tokenSession = tokenDAO.findById(securityToken);
		if (tokenSession == null) {
			throw new NotAuthorizedException("Invalid session...");
		}

		UserEntity user = userDAO.findById(tokenSession.getUserId());
		if (!user.ownsPersona(personaId)) {
			throw new NotAuthorizedException("Persona is not owned by user");
		}
		return true;
	}

	public void deleteByUserId(Long userId) {
		TokenSessionEntity token = tokenDAO.findByUserId(userId);
		if (token != null) {
			if (token.getCryptoTicket() != null) {
				revokeCryptoTicket(token.getCryptoTicket());
			}
			tokenDAO.deleteByUserId(userId);
		}
	}

	private Date getMinutes(int minutes) {
		long time = new Date().getTime();
		time = time + (minutes * 60000);
		Date date = new Date(time);
		return date;
	}

	public LoginStatusVO checkGeoIp(String ip) {
		LoginStatusVO loginStatusVO = new LoginStatusVO(0L, "", false);
		String allowedCountries = serverInfoBO.getServerInformation().getAllowedCountries();
		if (allowedCountries != null && !allowedCountries.isEmpty()) {
			String geoip2DbFilePath = parameterBO.getStrParam("GEOIP2_DB_FILE_PATH");
			GeoIp2 geoIp2 = GeoIp2.getInstance(geoip2DbFilePath);
			if (geoIp2.isCountryAllowed(ip, allowedCountries)) {
				return new LoginStatusVO(0L, "", true);
			} else {
				loginStatusVO.setDescription("GEOIP BLOCK ACTIVE IN THIS SERVER, ALLOWED COUNTRIES: [" + allowedCountries + "]");
			}
		} else {
			return new LoginStatusVO(0L, "", true);
		}
		return loginStatusVO;
	}

	public LoginStatusVO login(String email, String password, HttpServletRequest httpRequest) {
		LoginStatusVO loginStatusVO = checkGeoIp(httpRequest.getRemoteAddr());
		if (!loginStatusVO.isLoginOk()) {
			return loginStatusVO;
		}

		loginStatusVO = new LoginStatusVO(0L, "", false);

		if (email != null && !email.isEmpty() && password != null && !password.isEmpty()) {
			UserEntity userEntity = userDAO.findByEmail(email);

			if (userEntity != null) {
				if(userEntity.isAdmin() || parameterBO.getBoolParam("IS_MAINTENANCE") == false) {
					if (password.equals(userEntity.getPassword())) {
						BanEntity banEntity = authenticationBO.checkUserBan(userEntity);

						if (banEntity != null) {
							LoginStatusVO.Ban ban = new LoginStatusVO.Ban();
							ban.setReason(banEntity.getReason());
							if (banEntity.getEndsAt() != null)
								ban.setExpires(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withZone(ZoneId.systemDefault()).format(banEntity.getEndsAt()));
							loginStatusVO.setBan(ban);
							return loginStatusVO;
						}

						//check if hwid is banned//
						BanEntity banEntity2 = authenticationBO.checkHWIDBan(httpRequest.getHeader("X-HWID"));
						if(banEntity2 != null) {
							LoginStatusVO.Ban ban = new LoginStatusVO.Ban();
							ban.setReason("This HWID is banned from another user. Please contact administration for more info.");
							loginStatusVO.setBan(ban);
							return loginStatusVO;
						}

						userEntity.setLastLogin(LocalDateTime.now());
						userEntity.setIpAddress(httpRequest.getRemoteAddr());
						userEntity.setDiscordId(httpRequest.getHeader("X-DiscordID"));

						if(httpRequest.getHeader("X-UserAgent") == null) {
							System.out.println("1" + httpRequest.getHeader("X-User-Agent"));
							userEntity.setUA(httpRequest.getHeader("X-User-Agent"));
						} else {
							System.out.println("2" + httpRequest.getHeader("X-UserAgent"));
							userEntity.setUA(httpRequest.getHeader("X-UserAgent"));
						}

						userDAO.update(userEntity);

						Long userId = userEntity.getId();
						deleteByUserId(userId);
						String randomUUID = createToken(userId, null);
						loginStatusVO = new LoginStatusVO(userId, randomUUID, true);
						loginStatusVO.setDescription("");

						return loginStatusVO;
					}
				} else {
					loginStatusVO.setDescription("Server is in maintenance. Please follow our discord for more info.");
         			return loginStatusVO;
				}
			}
		}

		loginStatusVO.setDescription("LOGIN ERROR");
		return loginStatusVO;
	}

	public Long getActivePersonaId(String securityToken) {
		TokenSessionEntity tokenSessionEntity = tokenDAO.findById(securityToken);
		return tokenSessionEntity.getActivePersonaId();
	}

	public void setActivePersonaId(String securityToken, Long personaId, Boolean isLogout) {
		TokenSessionEntity tokenSessionEntity = tokenDAO.findById(securityToken);

		if (!isLogout) {
			if (!userDAO.findById(tokenSessionEntity.getUserId()).ownsPersona(personaId)) {
				throw new NotAuthorizedException("Persona not owned by user");
			}
		}

		tokenSessionEntity.setActivePersonaId(personaId);
		tokenSessionEntity.setIsLoggedIn(!isLogout);
		tokenDAO.update(tokenSessionEntity);
	}

	public String getActiveRelayCryptoTicket(String securityToken) {
		TokenSessionEntity tokenSessionEntity = tokenDAO.findById(securityToken);
		return tokenSessionEntity.getRelayCryptoTicket();
	}

	public Long getActiveLobbyId(String securityToken) {
		TokenSessionEntity tokenSessionEntity = tokenDAO.findById(securityToken);
		return tokenSessionEntity.getActiveLobbyId();
	}

	public void setActiveLobbyId(String securityToken, Long lobbyId) {
		TokenSessionEntity tokenSessionEntity = tokenDAO.findById(securityToken);
		tokenSessionEntity.setActiveLobbyId(lobbyId);
		tokenDAO.update(tokenSessionEntity);
	}

	public String getCryptoTicket(String securityToken) {
		if (!parameterBO.getBoolParam("FREEROAM_TS_ENABLED")) {
			ByteBuffer byteBuffer = ByteBuffer.allocate(32);
			byteBuffer.put(new byte[] { 10, 11, 12, 13 });
			byte[] cryptoTicketBytes = byteBuffer.array();
			return Base64.getEncoder().encodeToString(cryptoTicketBytes);
		}
		TokenSessionEntity token = tokenDAO.findById(securityToken);
		if (token.getCryptoTicket() != null) {
			return token.getCryptoTicket();
		}
		UserEntity user = userDAO.findById(token.getUserId());
		TSTicketRequest req = new TSTicketRequest();
		for (PersonaEntity persona : user.getListOfProfile()) {
			req.addPersona(persona.getPersonaId());
		}
		TSTicketResponse res = ClientBuilder.newClient()
				.target(parameterBO.getStrParam("FREEROAM_TS_ENDPOINT"))
				.path("/api/v1/tickets/request")
				.request(MediaType.APPLICATION_JSON_TYPE)
				.header("Authorization", "Bearer " + parameterBO.getStrParam("FREEROAM_TS_APIKEY"))
				.post(Entity.json(req), TSTicketResponse.class);
		token.setCryptoTicket(res.getTicket());
		tokenDAO.update(token);
		return res.getTicket();
	}

	public void revokeCryptoTicket(String ticket) {
		if (!parameterBO.getBoolParam("FREEROAM_TS_ENABLED")) return;
		TSRevokeRequest req = new TSRevokeRequest(ticket);
		ClientBuilder.newClient()
				.target(parameterBO.getStrParam("FREEROAM_TS_ENDPOINT"))
				.path("/api/v1/tickets/revoke")
				.request(MediaType.APPLICATION_JSON_TYPE)
				.header("Authorization", "Bearer " + parameterBO.getStrParam("FREEROAM_TS_APIKEY"))
				.post(Entity.json(req));
	}

	public boolean isPremium(String securityToken) {
		return tokenDAO.findById(securityToken).isPremium();
	}

	public boolean isAdmin(String securityToken) {
		return getUser(securityToken).isAdmin();
	}

	public UserEntity getUser(String securityToken) {
		return userDAO.findById(tokenDAO.findById(securityToken).getUserId());
	}
}