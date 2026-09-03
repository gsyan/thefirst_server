package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
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
    private EModuleType moduleType;
    private String moduleSubType;
    private Integer moduleLevel;
    private Integer hullIndex;
    private Integer slotIndex;
    private Integer attackPoints;
    private Integer attackToFighterPoints;
}
