package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * InterceptorFormula
 * Auto-generated from Unity C# InterceptorFormula class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class InterceptorFormula {
    private Float delayReductionPerPoint;

    private Float regenRatePerPoint;

    private Float delayFloor;
}
