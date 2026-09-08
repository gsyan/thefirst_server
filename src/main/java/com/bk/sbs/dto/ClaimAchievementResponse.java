package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClaimAchievementResponse
 * Auto-generated from Unity C# ClaimAchievementResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ClaimAchievementResponse {
    private String achievementId;
    private Integer achievementPointReward;
    private Integer achievementPointRemain;
}
