package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClearExplorationCellResponse
 * Auto-generated from Unity C# ClearExplorationCellResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ClearExplorationCellResponse {
    private Integer mineralGained;
    private Integer scavengedMineral;
    private Integer scoutedMineral;
    private Integer expGained;
    private Integer totalExp;
    private Integer commanderLevel;
    private List<String> rewardCardCandidates;
    private List<TreasureChoiceInfoDto> treasureChoices;
    private Integer resource;
    private Integer mineralAmount;
    private Integer rerollRemain;
    private List<HullArchetypeProgressInfoDto> hullArchetypePassiveProgress;
    private FleetInfoDto fleetInfo;
}
