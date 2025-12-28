package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleInfoDto
 * Auto-generated from Unity C# ModuleInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleInfoDto {
    private Integer moduleTypePacked;
    private Integer moduleLevel;
    private Integer bodyIndex;
    private Integer slotIndex;
}
