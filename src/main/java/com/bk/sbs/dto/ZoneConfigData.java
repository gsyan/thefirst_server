package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneConfigData
 * Auto-generated from Unity C# ZoneConfig class (server-required fields only)
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneConfigData {
    private Integer zoneIndex;
    private Integer gridWidth;
    private Integer gridHeight;
    private Integer enemyFleetsPerCell;
    private Integer enemyBudget;
    private Integer enemyMaxCost;
    private Integer enemyDeviation;
    private Integer enemyMaxShipsPerFleet;
}
