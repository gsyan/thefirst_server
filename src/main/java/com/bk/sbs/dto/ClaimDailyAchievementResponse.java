package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClaimDailyAchievementResponse
 * Auto-generated from Unity C# ClaimDailyAchievementResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ClaimDailyAchievementResponse {
    private String achievementId;
    private Integer achievementPointReward;
    private Integer achievementPointRemain;
}
