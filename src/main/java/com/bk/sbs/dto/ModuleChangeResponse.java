package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleChangeResponse {
    private boolean success;
    private ShipDto updatedShipInfo;
    private CostRemainInfo costRemainInfo;
    private String message;
}
