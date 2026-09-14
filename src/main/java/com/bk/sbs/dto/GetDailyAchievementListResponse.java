package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetDailyAchievementListResponse
 * Auto-generated from Unity C# GetDailyAchievementListResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class GetDailyAchievementListResponse {
    private List<DailyAchievementStatusDto> achievements;
}
