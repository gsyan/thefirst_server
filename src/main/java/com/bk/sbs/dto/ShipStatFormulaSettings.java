package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ShipStatFormulaSettings
 * Auto-generated from Unity C# ShipStatFormulaSettings class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ShipStatFormulaSettings {
    private Integer maxModuleSlots;

    private BeamFormula beam;

    private MissileFormula missile;

    private HangarFormula hangar;

    private ShieldFormula shield;

    private InterceptorFormula interceptor;

    private FlatStatFormula flatStats;
}
