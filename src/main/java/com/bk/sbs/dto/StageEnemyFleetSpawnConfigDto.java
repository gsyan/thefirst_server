package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * StageEnemyFleetSpawnConfigDto
 * Auto-generated from Unity C# StageEnemyFleetSpawnConfig class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class StageEnemyFleetSpawnConfigDto {
    private Integer fleetIndex;
    private Integer positionIndex;
    private FleetInfoDto fleetInfo;
}
