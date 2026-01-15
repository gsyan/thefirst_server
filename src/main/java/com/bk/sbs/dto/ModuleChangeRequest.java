package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleChangeRequest
 * Auto-generated from Unity C# ModuleChangeRequest class
 */
@Data
@NoArgsConstructor
public class ModuleChangeRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleTypeCurrent;
    private EModuleSubType moduleSubTypeCurrent;
    private EModuleType moduleTypeNew;
    private EModuleSubType moduleSubTypeNew;
    private Integer slotIndex;
}
