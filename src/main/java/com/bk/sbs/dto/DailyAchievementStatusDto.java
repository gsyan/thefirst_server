package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DailyAchievementStatusDto
 * Auto-generated from Unity C# DailyAchievementStatus class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DailyAchievementStatusDto {
    private String achievementId;
    private Integer currentValue;
    private Boolean isClaimed;
}
