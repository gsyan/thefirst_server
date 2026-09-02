package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleHullInfoDto
 * Auto-generated from Unity C# ModuleHullInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleHullInfoDto {
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private Integer moduleLevel;
    private Integer hullIndex;
    private List<ModuleInfoDto> beams;
    private List<ModuleInfoDto> missiles;
    private List<ModuleInfoDto> hangars;
    private String shieldModuleSubType;
    private String interceptorModuleSubType;
    private Float currentHealth;
}
