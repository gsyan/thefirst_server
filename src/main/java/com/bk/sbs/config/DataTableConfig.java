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

    private Integer pvpMinCommanderLevel;

    private Integer pvpListCount;

    private Integer pvpListRefreshCount;

    private Integer pvpRankScoreInit;

    private Integer pvpRankScorePenalty;

    private Integer moduleUnlockPrice;

    private Integer repairBoostMineralPerSec;

    private Float repairBoostMultiplier;

    private Integer instantRepairBaseSecs;

    private Integer missileTacticMineralPerSec;

    private Float missileTacticDamageMultiplier;

    private Float missileTacticExplosionMultiplier;

    private Integer aircraftTacticMineralPerSec;

    private Float aircraftTacticDamageMultiplier;

    private Float aircraftTacticAmmoMultiplier;

    private ShipStatFormulaSettings shipStatFormula;

}
