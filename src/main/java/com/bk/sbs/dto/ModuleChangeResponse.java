package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleChangeResponse
 * Auto-generated from Unity C# ModuleChangeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleChangeResponse {
    private Boolean success;
    private ShipInfoDto updatedShipInfo;
    private CostRemainInfoDto costRemainInfo;
    private String message;
}
