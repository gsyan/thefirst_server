package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EscapeExplorationZoneResponse
 * Auto-generated from Unity C# EscapeExplorationZoneResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EscapeExplorationZoneResponse {
    private Integer expGained;
    private Integer totalExp;
    private Integer commanderLevel;
    private Integer highestClearedZoneNumber;
    private Integer resource;
    private Boolean hasUnclaimedAchievement;
    private List<HullArchetypeProgressInfoDto> hullArchetypePassiveProgress;
    private Integer achievementPointGained;
    private Integer achievementPoint;
    private FleetInfoDto fleetInfo;
}
