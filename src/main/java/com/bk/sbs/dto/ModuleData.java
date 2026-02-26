package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
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

    private EModuleType moduleType;

    private EModuleSubType moduleSubType;

    private Integer moduleLevel;

    private List<ModuleSlotInfoDto> moduleSlots;

    private CostStructDto upgradeCost;

    private String description;

    private Float health;

    private Float repairPower;

    private Float speed;

    private Integer attackFireCount;

    private Float attackPower;

    private Float attackCoolTime;

    private Float projectileWidth;

    private Float projectileSpeed;

    private Integer airCount;

    private Float maintenanceTime;

    private Float aircraftLaunchStraightDistance;

    private Float aircraftHealth;

    private Float aircraftAttackPower;

    private Float aircraftAttackRange;

    private Float aircraftAttackCooldown;

    private Float aircraftSpeed;

    private Integer aircraftAmmo;

    private Float aircraftDetectionRadius;

    private Float aircraftAvoidanceRadius;
}
