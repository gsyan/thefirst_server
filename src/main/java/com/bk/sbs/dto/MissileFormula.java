package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MissileFormula
 * Auto-generated from Unity C# MissileFormula class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MissileFormula {
    private Float attackPerPoint;

    private Float maxCoolReductionRatio;

    private Float attackCoolFloor;

    private Float silenceTimePerPoint;
}
