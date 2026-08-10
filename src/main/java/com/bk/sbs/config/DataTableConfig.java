package com.bk.sbs.config;

import com.bk.sbs.dto.ShipStatFormulaSettings;
import lombok.Data;

/**
 * DataTableConfig
 * Auto-generated from Unity C# DataTableConfig class
 */
@Data
public class DataTableConfig {
    private String version;

    private Integer addShipCost;

    private Integer commandPowerMaxInit;

    private Integer pvpMinCommanderLevel;

    private Integer pvpListCount;

    private Integer pvpListRefreshCount;

    private Integer pvpRankScoreInit;

    private Integer pvpRankScorePenalty;

    private Integer moduleUnlockPrice;

    private Integer repairBoostExplorationPointPerSec;

    private Float repairBoostMultiplier;

    private Integer instantRepairBaseSecs;

    private Integer missileTacticExplorationPointPerSec;

    private Float missileTacticDamageMultiplier;

    private Float missileTacticExplosionMultiplier;

    private Integer aircraftTacticExplorationPointPerSec;

    private Float aircraftTacticDamageMultiplier;

    private Float aircraftTacticAmmoMultiplier;

    private ShipStatFormulaSettings shipStatFormula;

}
