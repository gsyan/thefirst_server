package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleGradeChangeRequest
 * Auto-generated from Unity C# ModuleGradeChangeRequest class
 */
@Data
@NoArgsConstructor
public class ModuleGradeChangeRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private EModuleSubType moduleSubTypeCurrent;
    private Integer slotIndex;
}
