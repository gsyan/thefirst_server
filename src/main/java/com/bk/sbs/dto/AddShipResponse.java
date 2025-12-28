package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AddShipResponse
 * Auto-generated from Unity C# AddShipResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class AddShipResponse {
    private Boolean success;
    private String message;
    private ShipInfoDto newShipInfo;
    private CostRemainInfoDto costRemainInfo;
    private FleetInfoDto updatedFleetInfo;
}
