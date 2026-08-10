package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DailyClaimResponse
 * Auto-generated from Unity C# DailyClaimResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DailyClaimResponse {
    private Boolean available;
    private Integer grantedExplorationPoint;
    private Integer explorationPointRemain;
    private String nextAvailableAt;
    private Integer todayDay;
    private Integer claimedDaysMask;
    private Integer vipClaimedDaysMask;
    private Integer loginRewardMonth;
}
