package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleGradeChangeResponse
 * Auto-generated from Unity C# ModuleGradeChangeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleGradeChangeResponse {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleTypeCurrent;
    private EModuleSubType moduleSubTypeCurrent;
    private EModuleType moduleTypeNew;
    private EModuleSubType moduleSubTypeNew;
    private Integer slotIndex;
    private Integer moduleNewLevel;
    private Integer pointRemain;
    private Integer investedPoint;
    private Boolean isModuleRemoved;
    private Boolean isShipRemoved;
    private Long removedShipId;
}
