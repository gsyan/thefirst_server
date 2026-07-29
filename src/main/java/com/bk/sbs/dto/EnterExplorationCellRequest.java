package com.bk.sbs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EnterExplorationCellRequest
 * Auto-generated from Unity C# EnterExplorationCellRequest class
 */
@Data
@NoArgsConstructor
public class EnterExplorationCellRequest {
    private Integer zoneNumber;
    private Integer cellRow;
    private Integer cellCol;
    private FleetInfoDto fleetInfo;
}
