package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AbandonZoneRunResponse
 * Auto-generated from Unity C# AbandonZoneRunResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class AbandonZoneRunResponse {
    private Integer explorationPointGained;
    private Integer explorationPointRemain;
    private Integer expGained;
    private Integer totalExp;
    private Integer commanderLevel;
}
