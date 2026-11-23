package com.bk.sbs.dto;

import com.bk.sbs.enums.ETreasureRewardType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SelectTreasureRewardRequest
 * Auto-generated from Unity C# SelectTreasureRewardRequest class
 */
@Data
@NoArgsConstructor
public class SelectTreasureRewardRequest {
    private Integer zoneNumber;
    private ETreasureRewardType rewardType;
}
