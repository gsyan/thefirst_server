package com.bk.sbs.config;

import com.bk.sbs.dto.CostStructDto;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import lombok.Data;

/**
 * DataTableConfig
 * Auto-generated from Unity C# DataTableConfig class
 */
@Data
public class DataTableConfig {
    @JsonAlias("m_version")
    private String version;

    @JsonAlias("m_maxLives")
    private Integer maxLives;

    @JsonAlias("m_maxShipsPerFleet")
    private Integer maxShipsPerFleet;

    @JsonAlias("m_addShipCosts")
    private List<CostStructDto> addShipCosts;

    @JsonAlias("m_moduleUnlockPrice")
    private Integer moduleUnlockPrice;

    @JsonAlias("m_enemyFleetSpawnInterval")
    private Float enemyFleetSpawnInterval;

    @JsonAlias("m_explorationInterval")
    private Float explorationInterval;

    @JsonAlias("m_enemySpawnRate")
    private Float enemySpawnRate;

}
