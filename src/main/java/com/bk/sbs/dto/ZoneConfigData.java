package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneConfigData
 * Auto-generated from Unity C# ZoneStageConfig class (server-required fields only)
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneConfigData {
    private String zoneName;
    private List<StageEnemyFleetSpawnConfigDto> enemyFleets;
    private Integer mineralClearReward;
    private Integer techPointClearReward;
    private Integer modulePointClearReward;
}
