package com.soapboxrace.core.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.soapboxrace.jaxb.http.AchievementRankPacket;

@Entity
@Table(name = "ACHIEVEMENT_RANK")
public class AchievementRankEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@JoinColumn(name = "achievementId", referencedColumnName = "id", foreignKey = @ForeignKey(name = "FK_ACHRANK_ACHDEF"))
	@ManyToOne
	private AchievementDefinitionEntity achievementDefinition;

	@Column(name = "isRare")
	private boolean isRare;

	@Column(name = "points")
	private short points;

	@Column(name = "rank")
	private short rank;

	@Column(name = "rewardDescription")
	private String rewardDescription;

	@Column(name = "rewardType")
	private String rewardType;

	@Column(name = "rewardVisualStyle")
	private String rewardVisualStyle;

	@Column(name = "thresholdValue")
	private Long thresholdValue;

	public AchievementRankPacket toBasePacket() {
		AchievementRankPacket packet = new AchievementRankPacket();
		packet.setAchievementRankId(id.intValue());
		packet.setIsRare(isRare);
		packet.setRarity(0.0f);
		packet.setRank(rank);
		packet.setThresholdValue(thresholdValue);
		packet.setRewardDescription(rewardDescription);
		packet.setRewardType(rewardType);
		packet.setRewardVisualStyle(rewardVisualStyle);
		packet.setPoints(points);

		return packet;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public AchievementDefinitionEntity getAchievementDefinition() {
		return achievementDefinition;
	}

	public void setAchievementDefinition(AchievementDefinitionEntity achievementDefinition) {
		this.achievementDefinition = achievementDefinition;
	}

	public boolean isRare() {
		return isRare;
	}

	public void setRare(boolean rare) {
		isRare = rare;
	}

	public short getPoints() {
		return points;
	}

	public void setPoints(short points) {
		this.points = points;
	}

	public short getRank() {
		return rank;
	}

	public void setRank(short rank) {
		this.rank = rank;
	}

	public String getRewardDescription() {
		return rewardDescription;
	}

	public void setRewardDescription(String rewardDescription) {
		this.rewardDescription = rewardDescription;
	}

	public String getRewardType() {
		return rewardType;
	}

	public void setRewardType(String rewardType) {
		this.rewardType = rewardType;
	}

	public String getRewardVisualStyle() {
		return rewardVisualStyle;
	}

	public void setRewardVisualStyle(String rewardVisualStyle) {
		this.rewardVisualStyle = rewardVisualStyle;
	}

	public Long getThresholdValue() {
		return thresholdValue;
	}

	public void setThresholdValue(Long thresholdValue) {
		this.thresholdValue = thresholdValue;
	}
}