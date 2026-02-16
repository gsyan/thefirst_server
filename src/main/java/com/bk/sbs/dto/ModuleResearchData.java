package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias("m_researchId")
    private String researchId;

    @JsonAlias("m_prerequisiteIds")
    private List<String> prerequisiteIds;

    @JsonAlias("m_researchCost")
    private CostStructDto researchCost;

    @JsonAlias("m_moduleType")
    private EModuleType moduleType;

    @JsonAlias("m_moduleSubType")
    private EModuleSubType moduleSubType;

    @JsonAlias("m_description")
    private String description;
}
