package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import java.util.List;
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
    private EModuleSubType moduleSubType;
    private Integer moduleLevel;
    private Integer bodyIndex;
    private Integer slotIndex;
    private List<EModuleSubType> unlockedSubTypes;
    private Integer investedMineral;
    private Integer investedPvpMineral;
    private Integer investedTempMineral;
}
