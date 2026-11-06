package com.bk.sbs.dto;

import com.bk.sbs.enums.ETreasureRewardType;
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
    private List<String> rewardCardCandidates;
    private ETreasureRewardType treasureRewardType;
    private Float treasureRewardRatio;
    private Integer resource;
    private Integer mineralAmount;
    private Integer rerollRemain;
}
