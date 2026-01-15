package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSlotType;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleSlotInfoDto
 * Auto-generated from Unity C# ModuleSlotInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleSlotInfoDto {
    private EModuleType moduleType;
    private EModuleSubType moduleSubType;
    private EModuleSlotType moduleSlotType;
    private Integer slotIndex;
}
