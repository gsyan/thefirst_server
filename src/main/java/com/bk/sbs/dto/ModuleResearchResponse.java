package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleResearchResponse
 * Auto-generated from Unity C# ModuleResearchResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleResearchResponse {
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private CostRemainInfoDto costRemainInfo;
    private List<List<Integer>> researchedModuleTypes;
}
