package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleInstallRequest
 * Auto-generated from Unity C# ModuleInstallRequest class
 */
@Data
@NoArgsConstructor
public class ModuleInstallRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private Integer moduleLevel;
    private Integer slotIndex;
}
