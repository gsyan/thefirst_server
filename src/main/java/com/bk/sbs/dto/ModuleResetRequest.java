package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleResetRequest
 * Auto-generated from Unity C# ModuleResetRequest class
 */
@Data
@NoArgsConstructor
public class ModuleResetRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private Integer slotIndex;
}
