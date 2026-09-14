package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClaimAllDailyAchievementsResponse
 * Auto-generated from Unity C# ClaimAllDailyAchievementsResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ClaimAllDailyAchievementsResponse {
    private List<String> claimedAchievementIds;
    private Integer totalAchievementPointGranted;
    private Integer achievementPointRemain;
}
