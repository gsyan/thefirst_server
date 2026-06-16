package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MineralModuleGradeChangeResponse
 * Auto-generated from Unity C# MineralModuleGradeChangeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MineralModuleGradeChangeResponse {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleTypeCurrent;
    private EModuleSubType moduleSubTypeCurrent;
    private EModuleType moduleTypeNew;
    private EModuleSubType moduleSubTypeNew;
    private Integer slotIndex;
    private Integer moduleNewLevel;
    private Integer mineralRemain;
    private Integer investedMineral;
    private Boolean shipRemoved;
    private Long removedShipId;
}
