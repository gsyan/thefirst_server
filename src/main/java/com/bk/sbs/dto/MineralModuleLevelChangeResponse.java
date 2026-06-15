package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MineralModuleLevelChangeResponse
 * Auto-generated from Unity C# MineralModuleLevelChangeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MineralModuleLevelChangeResponse {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private Integer slotIndex;
    private Integer newLevel;
    private Integer mineralRemain;
    private Integer investedMineral;
}
