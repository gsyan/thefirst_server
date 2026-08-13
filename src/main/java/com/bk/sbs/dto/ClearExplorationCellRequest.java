package com.bk.sbs.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClearExplorationCellRequest
 * Auto-generated from Unity C# ClearExplorationCellRequest class
 */
@Data
@NoArgsConstructor
public class ClearExplorationCellRequest {
    private Integer zoneNumber;
    private Integer cellRow;
    private Integer cellCol;
    private List<ShipHealthRatioInfoDto> shipHealthRatios;
    private String challengeToken;
}
