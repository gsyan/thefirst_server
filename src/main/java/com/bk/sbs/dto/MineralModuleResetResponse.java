package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MineralModuleResetResponse
 * Auto-generated from Unity C# MineralModuleResetResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MineralModuleResetResponse {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private Integer slotIndex;
    private Integer moduleNewLevel;
    private Boolean isModuleRemoved;
    private Integer mineralRemain;
    private Integer investedMineral;
}
