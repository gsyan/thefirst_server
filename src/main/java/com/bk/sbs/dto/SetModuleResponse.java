package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SetModuleResponse
 * Auto-generated from Unity C# SetModuleResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SetModuleResponse {
    private ModuleBodyInfoDto body;
    private Integer commandCost;
    private Integer remainingCommandPower;
}
