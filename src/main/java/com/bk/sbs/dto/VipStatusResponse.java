package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VipStatusResponse
 * Auto-generated from Unity C# VipStatusResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class VipStatusResponse {
    private Boolean isVip;
    private String vipExpiry;
    private Integer dailyMineralAmount;
    private Integer mineralRewardMultiplier;
    private Integer pendingMineralTotal;
    private Integer claimedDaysMask;
    private Integer loginRewardMonth;
}
