package com.bk.sbs.dto;

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
    private Integer moduleTypePacked;
    private CostRemainInfoDto costRemainInfo;
    private List<Integer> researchedModuleTypePacked;
}
