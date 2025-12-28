package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleUpgradeResponse
 * Auto-generated from Unity C# ModuleUpgradeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleUpgradeResponse {
    private Boolean success;
    private Integer newLevel;
    private ModuleStatsDto newStats;
    private CostRemainInfoDto costRemainInfo;
    private String message;
}
