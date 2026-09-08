package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetAchievementListResponse
 * Auto-generated from Unity C# GetAchievementListResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class GetAchievementListResponse {
    private List<AchievementStatusDto> achievements;
}
