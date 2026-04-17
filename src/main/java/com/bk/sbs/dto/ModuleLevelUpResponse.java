package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleLevelUpResponse
 * Auto-generated from Unity C# ModuleLevelUpResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleLevelUpResponse {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private Integer slotIndex;
    private Integer newLevel;
    private CostRemainInfoDto costRemainInfo;
}
