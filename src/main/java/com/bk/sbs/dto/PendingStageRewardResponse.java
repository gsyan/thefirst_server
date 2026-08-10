package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PendingStageRewardResponse
 * Auto-generated from Unity C# PendingStageRewardResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PendingStageRewardResponse {
    private Integer expGained;
    private Integer commanderLevel;
    private Integer totalExp;
}
