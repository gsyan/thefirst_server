package com.bk.sbs.config;

import com.bk.sbs.dto.CostStructDto;
import java.util.List;
import lombok.Data;

/**
 * DataTableConfig
 * Auto-generated from Unity C# DataTableConfig class
 */
@Data
public class DataTableConfig {
    private String version;
    private Integer maxLives;
    private Integer maxShipsPerFleet;
    private List<CostStructDto> addShipCosts;
    private Integer moduleUnlockPrice;
    private Float enemyFleetSpawnInterval;
    private Float explorationInterval;
    private Float enemySpawnRate;
}
