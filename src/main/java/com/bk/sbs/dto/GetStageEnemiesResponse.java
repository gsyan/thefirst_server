package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetStageEnemiesResponse
 * Auto-generated from Unity C# GetStageEnemiesResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class GetStageEnemiesResponse {
    private String zoneName;
    private List<StageEnemyFleetSpawnConfigDto> enemyFleets;
}
