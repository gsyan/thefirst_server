package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MineralModuleGradeChangeRequest
 * Auto-generated from Unity C# MineralModuleGradeChangeRequest class
 */
@Data
@NoArgsConstructor
public class MineralModuleGradeChangeRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private EModuleSubType moduleSubTypeCurrent;
    private EModuleSubType moduleSubTypeNew;
    private Integer slotIndex;
}
