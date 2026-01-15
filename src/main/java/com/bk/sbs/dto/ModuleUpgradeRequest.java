package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleUpgradeRequest
 * Auto-generated from Unity C# ModuleUpgradeRequest class
 */
@Data
@NoArgsConstructor
public class ModuleUpgradeRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private Integer slotIndex;
    private Integer currentLevel;
    private Integer targetLevel;
}
