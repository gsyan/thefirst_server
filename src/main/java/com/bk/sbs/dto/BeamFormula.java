package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BeamFormula
 * Auto-generated from Unity C# BeamFormula class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class BeamFormula {
    private Integer installCost;

    private Float baseAttack;

    private Float attackPerPoint;

    private Float baseAttackCool;

    private Float attackCoolReductionPerPoint;

    private Float attackCoolFloor;

    private Float baseProjectileSpeed;

    private Float projectileSpeedPerPoint;
}
