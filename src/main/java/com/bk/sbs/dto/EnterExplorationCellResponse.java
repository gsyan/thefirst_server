package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EnterExplorationCellResponse
 * Auto-generated from Unity C# EnterExplorationCellResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EnterExplorationCellResponse {
    private Integer zoneNumber;
    private Integer cellX;
    private Integer cellY;
    private List<StageEnemyFleetSpawnConfigDto> enemyFleets;
}
