package com.bk.sbs.dto;

import com.bk.sbs.enums.ETreasureRewardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SelectTreasureRewardResponse
 * Auto-generated from Unity C# SelectTreasureRewardResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SelectTreasureRewardResponse {
    private ETreasureRewardType rewardType;
    private Float ratio;
    private Integer mineralGained;
    private Integer mineralAmount;
    private Integer resource;
}
