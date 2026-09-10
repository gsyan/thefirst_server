package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClaimAllAchievementsResponse
 * Auto-generated from Unity C# ClaimAllAchievementsResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ClaimAllAchievementsResponse {
    private List<String> claimedAchievementIds;
    private Integer totalAchievementPointGranted;
    private Integer achievementPointRemain;
}
