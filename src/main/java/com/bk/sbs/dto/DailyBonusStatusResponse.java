package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DailyBonusStatusResponse
 * Auto-generated from Unity C# DailyBonusStatusResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DailyBonusStatusResponse {
    private Boolean available;
    private Integer todayDay;
    private Integer claimedDaysMask;
    private Integer vipClaimedDaysMask;
    private String loginRewardWeekStart;
    private String nextAvailableAt;
}
