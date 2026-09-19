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
    private Integer grantedAchievementPoint;
    private Integer explorationPointRemain;
    private Integer achievementPointRemain;
    private Integer todayDay;
    private Integer claimedDaysMask;
    private Integer vipClaimedDaysMask;
}
