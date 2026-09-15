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

    private Integer tacticMissileCost;

    private Integer tacticHangerCost;

    private Integer tacticShieldCost;

    private Integer tacticInterceptorCost;
}
