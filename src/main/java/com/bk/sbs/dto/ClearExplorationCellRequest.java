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
    private Integer resource;
    private Integer mineralAmount;
    private String challengeToken;
    private Integer enemyShipKills;
    private Integer aircraftLaunches;
    private Integer scavengedMineral;
    private Integer scoutedMineral;
    private List<ArchetypeCounterReportDto> archetypeCounters;
    private Boolean isMissileOnlyClear;
    private Boolean isAircraftOnlyClear;
    private Boolean isNoShieldClear;
    private Integer shieldAbsorbAmount;
    private Integer interceptCount;
    private Integer fleetRepairAmount;
}
