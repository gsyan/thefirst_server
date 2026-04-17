package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleLevelUpRequest
 * Auto-generated from Unity C# ModuleLevelUpRequest class
 */
@Data
@NoArgsConstructor
public class ModuleLevelUpRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private Integer slotIndex;
    private Integer currentLevel;
    private Integer targetLevel;
}
