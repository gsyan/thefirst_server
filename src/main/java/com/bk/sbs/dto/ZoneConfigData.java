package com.bk.sbs.dto;

import java.util.List;
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
    private List<GridCellOverrideDto> cellOverrides;
    private Integer enemyFleetsPerCell;
    private Integer enemyBudget;
    private Integer enemyMaxCostOfOneShip;
    private Integer enemyDeviation;
    private Integer enemyMaxShipsPerFleet;
    private Float enemyHealthMultiplier;
    private Float enemyAttackMultiplier;
    private Integer enemyBeamEquipSlots;
    private Integer enemyMissileEquipSlots;
    private Integer enemyHangarEquipSlots;
    private Integer enemyShieldEquipSlots;
    private Integer enemyInterceptorEquipSlots;
    private Integer explorationPointReward;
    private Integer commanderExpReward;
}
