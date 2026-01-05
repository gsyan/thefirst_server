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
    private Long shipId;
    private Integer bodyIndex;
    private Integer moduleTypePacked;
    private Integer slotIndex;
    private Integer newLevel;
    private CostRemainInfoDto costRemainInfo;
}
