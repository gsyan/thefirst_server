package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ShieldFormula
 * Auto-generated from Unity C# ShieldFormula class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ShieldFormula {
    private Float gaugePerPoint;

    private Float delayReductionPerPoint;

    private Float regenRatePerPoint;

    private Float delayFloor;
}
