package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PendingStageRewardResponse
 * Auto-generated from Unity C# PendingStageRewardResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PendingStageRewardResponse {
    private Integer mineralGained;
    private Integer expGained;
    private Integer modulePointGained;
    private Integer mineralRemain;
    private Integer commanderLevel;
    private Integer totalExp;
    private Integer modulePointRemain;
    private Integer modulePointMaxGot;
    private Boolean mineralSettingReset;
    private FleetInfoDto updatedFleetInfo;
}
