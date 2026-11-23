package com.bk.sbs.dto;

import com.bk.sbs.enums.ETreasureRewardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TreasureChoiceInfoDto
 * Auto-generated from Unity C# TreasureChoiceInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class TreasureChoiceInfoDto {
    private ETreasureRewardType rewardType;
    private Integer mineralAmount;
    private Float ratio;
}
