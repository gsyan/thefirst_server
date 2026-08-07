package com.bk.sbs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SetFleetPresetSlotModulesRequest
 * Auto-generated from Unity C# SetFleetPresetSlotModulesRequest class
 */
@Data
@NoArgsConstructor
public class SetFleetPresetSlotModulesRequest {
    private Integer slotIndex;
    private ModuleBodyInfoDto modules;
}
