package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleUnlockResponse
 * Auto-generated from Unity C# ModuleUnlockResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleUnlockResponse {
    private Boolean success;
    private ShipInfoDto updatedShipInfo;
    private CostRemainInfoDto costRemainInfo;
    private String message;
}
