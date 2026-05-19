package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
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

    private EModuleSubType moduleSubType;

    private Integer moduleLevel;

    private Integer modulePointCost;

    private Integer mineralCost;

    private String description;

    private List<ModuleSlotInfoDto> moduleSlots;

    private Float health;

    private Float repair;

    private Float speed;

    private Integer attackFireCount;

    private Float attack;

    private Float attackCool;

    private Float projectileSpeed;

    private Integer airCount;

    private Float airMaintenanceTime;

    private Float airLaunchDist;

    private Float airHealth;

    private Float airAttack;

    private Float airAttackRange;

    private Float airAttackCool;

    private Float airSpeed;

    private Integer airAmmo;

    private Float airDetectRadius;

    private Float airAvoidRadius;
}
