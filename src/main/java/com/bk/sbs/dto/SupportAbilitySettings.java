package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SupportAbilitySettings
 * Auto-generated from Unity C# SupportAbilitySettings class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SupportAbilitySettings {
    private Integer tickSeconds;

    private Integer repairShipCostPercent;

    private Integer repairReleaseRatioPercent;

    private Integer shieldChargeBonusPercent;

    private Integer shieldChargeCostPerTick;

    private Integer shieldChargeReleaseRatioPercent;
}
