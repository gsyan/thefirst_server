package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleData
 * Auto-generated from Unity C# ModuleData class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleData {
    private String moduleName;

    private String moduleSubType;

    private Integer statPoint;

    private Integer unlockCommanderLevel;

    private String description;

    private List<ModuleSlotInfoDto> moduleSlots;

    private Float health;

    private Float repair;

    private Float speed;

    private Float turnRate;

    private Float attack;

    private Float splashRadius;

    private Float attackCool;

    private Float silenceTime;

    private Integer airCount;

    private Float airMaintenanceTime;

    private Float airHealth;

    private Float airAttack;

    private Float airAttackRange;

    private Float airAttackCool;

    private Float airSpeed;

    private Integer airAmmo;

    private Float airDetectRadius;

    private Float airAvoidRadius;

    private Float airDisrupt;

    private Float shieldGauge;

    private Float shieldRegenRate;

    private Integer interceptorCount;

    private Float interceptorDelay;

    private Float interceptorRegenRate;
}
