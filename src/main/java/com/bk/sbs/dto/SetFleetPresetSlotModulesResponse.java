package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SetFleetPresetSlotModulesResponse
 * Auto-generated from Unity C# SetFleetPresetSlotModulesResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SetFleetPresetSlotModulesResponse {
    private ModuleBodyInfoDto body;
    private Integer commandCost;
    private Integer remainingCommandPower;
}
