package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AchievementStatusDto
 * Auto-generated from Unity C# AchievementStatus class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class AchievementStatusDto {
    private String achievementId;
    private Integer currentValue;
    private Boolean isClaimed;
    private Boolean isVipClaimed;
}
