package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CommanderInfoDto
 * Auto-generated from Unity C# CommanderInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CommanderInfoDto {
    private Long commanderId;
    private String commanderName;
    private Integer nameChangeCount;
    private Integer commanderLevel;
    private Integer exp;
    private Integer commandPowerMax;
    private Integer resourceMax;
    private Integer resource;
    private Integer mineralAmount;
    private Integer explorationSeedBase;
    private Integer mineralEarnedTotal;
    private Integer achievementPoint;
    private List<String> unlockedHulls;
    private Integer explorationZoneNumber;
    private String explorationCell;
    private Integer highestClearedZoneNumber;
    private Integer pvpPoint;
    private Integer pvpPointMaxGot;
    private String pvpPointExpiry;
    private Boolean hasUnclaimedAchievement;
    private List<ArchetypeUpgradeInfoDto> archetypeUpgrades;
    private List<HullArchetypeProgressInfoDto> hullArchetypePassiveProgress;
}
