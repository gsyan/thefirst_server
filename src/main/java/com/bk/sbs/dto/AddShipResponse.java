package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddShipResponse {
    private boolean success;
    private String message;
    private ShipDto newShipInfo;
    private CostRemainInfo costRemainInfo;
    private FleetDto updatedFleetInfo;

    // Constructor with parameters
    public AddShipResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}