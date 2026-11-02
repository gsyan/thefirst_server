package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ArchetypeUpgradeInfoDto
 * Auto-generated from Unity C# ArchetypeUpgradeInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ArchetypeUpgradeInfoDto {
    private String hullArchetype;
    private EModuleType moduleType;
    private Integer slotIndex;
    private Integer level;
}
