package com.soapboxrace.core.bo;

import javax.ejb.EJB;
import javax.ejb.Stateless;

import com.soapboxrace.core.jpa.EventSessionEntity;
import com.soapboxrace.core.jpa.PersonaEntity;
import com.soapboxrace.jaxb.http.ArbitrationPacket;
import com.soapboxrace.jaxb.http.DragArbitrationPacket;
import com.soapboxrace.jaxb.http.PursuitArbitrationPacket;
import com.soapboxrace.jaxb.http.RouteArbitrationPacket;
import com.soapboxrace.jaxb.http.TeamEscapeArbitrationPacket;

import com.soapboxrace.core.dao.PersonaDAO;

@Stateless
public class LegitRaceBO {

	@EJB
	private ParameterBO parameterBO;

	@EJB
	private SocialBO socialBo;

    @EJB
    private PersonaDAO personaDAO;

	public boolean isLegit(Long activePersonaId, ArbitrationPacket arbitrationPacket, EventSessionEntity sessionEntity) {
		int minimumTime = 0;

		if (arbitrationPacket instanceof PursuitArbitrationPacket)
			minimumTime = parameterBO.getIntParam("PURSUIT_MINIMUM_TIME");
		else if (arbitrationPacket instanceof RouteArbitrationPacket)
			minimumTime = parameterBO.getIntParam("ROUTE_MINIMUM_TIME");
		else if (arbitrationPacket instanceof TeamEscapeArbitrationPacket)
			minimumTime = parameterBO.getIntParam("TE_MINIMUM_TIME");
		else if (arbitrationPacket instanceof DragArbitrationPacket)
			minimumTime = parameterBO.getIntParam("DRAG_MINIMUM_TIME");

		final long timeDiff = sessionEntity.getEnded() - sessionEntity.getStarted();
		boolean legit = timeDiff > minimumTime + 1;

		if (!legit) {
			//SHADOWBAN THAT USER!
			PersonaEntity persona = personaDAO.findById(activePersonaId);
			persona.setShadowBanned(true);
			personaDAO.update(persona);


			socialBo.sendReport(0L, activePersonaId, 3, String.format("Abnormal event time: %d. User is ShadowBanned", timeDiff), (int) arbitrationPacket.getCarId(), 0, arbitrationPacket.getHacksDetected());
		}

		if (arbitrationPacket.getHacksDetected() != 0 && arbitrationPacket.getHacksDetected() != 32) {
			socialBo.sendReport(0L, activePersonaId, 3, "hacksDetected == " + arbitrationPacket.getHacksDetected(), (int) arbitrationPacket.getCarId(), 0,
					arbitrationPacket.getHacksDetected());
		}

		return legit;
	}
}
