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

    private Integer maxShipsPerFleet;

    private List<CostStructDto> addShipCosts;

    private Integer pvpListCount;

    private Integer pvpListRefreshCount;

    private Integer pvpRankScoreInit;

    private Integer pvpRankScorePenalty;

    private Integer moduleUnlockPrice;

}
