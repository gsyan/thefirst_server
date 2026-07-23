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
    private Integer installCost;

    private Float baseAttack;

    private Float attackPerPoint;

    private Float baseAttackCool;

    private Float attackCoolReductionPerPoint;

    private Float attackCoolFloor;

    private Float baseProjectileSpeed;

    private Float projectileSpeedPerPoint;

    private Float baseSilenceTime;

    private Float silenceTimePerPoint;
}
