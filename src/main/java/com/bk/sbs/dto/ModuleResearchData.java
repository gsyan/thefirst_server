package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleResearchData
 * Auto-generated from Unity C# ModuleResearchData class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleResearchData {
    private String researchId;

    private List<String> prerequisiteIds;

    private CostStructDto researchCost;

    private EModuleType moduleType;

    private EModuleSubType moduleSubType;
}
