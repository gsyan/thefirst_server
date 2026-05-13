package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ShipResetRemoveResponse
 * Auto-generated from Unity C# ShipResetRemoveResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ShipResetRemoveResponse {
    private Long removedShipId;
    private Integer modulePointRemain;
    private FleetInfoDto updatedFleetInfo;
}
