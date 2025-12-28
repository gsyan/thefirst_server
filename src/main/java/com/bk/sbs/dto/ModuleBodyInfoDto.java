package com.bk.sbs.dto;

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
    private Integer moduleTypePacked;
    private Integer moduleLevel;
    private Integer bodyIndex;
    private List<ModuleInfoDto> engines;
    private List<ModuleInfoDto> weapons;
    private List<ModuleInfoDto> hangers;
}
