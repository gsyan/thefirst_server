package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FleetResetAllInvestedMineralResponse
 * Auto-generated from Unity C# FleetResetAllInvestedMineralResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class FleetResetAllInvestedMineralResponse {
    private Integer mineralRemain;
    private Integer totalRefundedMineral;
    private Integer modulePointRemain;
    private FleetInfoDto updatedFleetInfo;
}
