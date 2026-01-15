package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleBodyInfoDto
 * Auto-generated from Unity C# ModuleBodyInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleBodyInfoDto {
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private Integer moduleLevel;
    private Integer bodyIndex;
    private List<ModuleInfoDto> engines;
    private List<ModuleInfoDto> weapons;
    private List<ModuleInfoDto> hangers;
}
