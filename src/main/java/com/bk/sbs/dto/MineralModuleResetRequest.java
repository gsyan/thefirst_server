package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MineralModuleResetRequest
 * Auto-generated from Unity C# MineralModuleResetRequest class
 */
@Data
@NoArgsConstructor
public class MineralModuleResetRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private Integer slotIndex;
}
