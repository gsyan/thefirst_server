package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleChangeResponse
 * Auto-generated from Unity C# ModuleChangeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleChangeResponse {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleTypeCurrent;
    private EModuleSubType moduleSubTypeCurrent;
    private EModuleType moduleTypeNew;
    private EModuleSubType moduleSubTypeNew;
    private Integer slotIndex;
}
