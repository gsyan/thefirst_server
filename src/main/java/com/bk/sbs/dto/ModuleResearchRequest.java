package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleResearchRequest
 * Auto-generated from Unity C# ModuleResearchRequest class
 */
@Data
@NoArgsConstructor
public class ModuleResearchRequest {
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
}
