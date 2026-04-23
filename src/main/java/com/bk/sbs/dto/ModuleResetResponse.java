package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleResetResponse
 * Auto-generated from Unity C# ModuleResetResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleResetResponse {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private Integer slotIndex;
    private CostRemainInfoDto costRemainInfo;
}
