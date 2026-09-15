package com.bk.sbs.config;

import com.bk.sbs.dto.ExplorationSettings;
import com.bk.sbs.dto.GeneralSettings;
import com.bk.sbs.dto.PvpSettings;
import com.bk.sbs.dto.ShipStatFormulaSettings;
import com.bk.sbs.dto.TacticSettings;
import lombok.Data;

/**
 * DataTableConfig
 * Auto-generated from Unity C# DataTableConfig class
 */
@Data
public class DataTableConfig {
    private GeneralSettings general;

    private PvpSettings pvp;

    private TacticSettings tactic;

    private ExplorationSettings exploration;

    private ShipStatFormulaSettings shipStatFormula;

}
