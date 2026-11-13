package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TacticSettings
 * Auto-generated from Unity C# TacticSettings class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class TacticSettings {
    private Integer tacticRepairCost;

    private Integer tacticBeamCost;

    private Integer tacticMissileCost;

    private Integer tacticHangerCost;

    private Integer tacticShieldCost;

    private Integer tacticInterceptorCost;

    private Integer mineralToResourceRatio;

    private Float berserkerThreshold1Percent;

    private Float berserkerThreshold2Percent;

    private Float berserkerThreshold3Percent;

    private Float berserkerAttackMultiplier1;

    private Float berserkerAttackMultiplier2;

    private Float berserkerAttackMultiplier3;
}
