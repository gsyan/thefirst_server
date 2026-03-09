package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleChangeCostEntryDto
 * Auto-generated from Unity C# ModuleChangeCostEntry class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleChangeCostEntryDto {
    private EModuleSubType moduleSubType;
    private CostStructDto cost;
}
