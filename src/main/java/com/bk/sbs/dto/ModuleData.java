package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias("m_moduleName")
    private String moduleName;

    @JsonAlias("m_moduleType")
    private EModuleType moduleType;

    @JsonAlias("m_moduleSubType")
    private EModuleSubType moduleSubType;

    @JsonAlias("m_moduleLevel")
    private Integer moduleLevel;

    @JsonAlias("m_moduleSlots")
    private List<ModuleSlotInfoDto> moduleSlots;

    @JsonAlias("m_upgradeCost")
    private CostStructDto upgradeCost;

    @JsonAlias("m_description")
    private String description;

    @JsonAlias("m_health")
    private Float health;

    @JsonAlias("m_cargoCapacity")
    private Float cargoCapacity;

    @JsonAlias("m_repairPower")
    private Float repairPower;

    @JsonAlias("m_movementSpeed")
    private Float movementSpeed;

    @JsonAlias("m_attackFireCount")
    private Integer attackFireCount;

    @JsonAlias("m_attackPower")
    private Float attackPower;

    @JsonAlias("m_attackCoolTime")
    private Float attackCoolTime;

    @JsonAlias("m_projectileWidth")
    private Float projectileWidth;

    @JsonAlias("m_projectileSpeed")
    private Float projectileSpeed;

    @JsonAlias("m_hangarCapability")
    private Integer hangarCapability;

    @JsonAlias("m_scoutCapability")
    private Integer scoutCapability;

    @JsonAlias("m_launchCool")
    private Float launchCool;

    @JsonAlias("m_launchCount")
    private Integer launchCount;

    @JsonAlias("m_maintenanceTime")
    private Float maintenanceTime;

    @JsonAlias("m_aircraftLaunchStraightDistance")
    private Float aircraftLaunchStraightDistance;

    @JsonAlias("m_aircraftHealth")
    private Float aircraftHealth;

    @JsonAlias("m_aircraftAttackPower")
    private Float aircraftAttackPower;

    @JsonAlias("m_aircraftAttackRange")
    private Float aircraftAttackRange;

    @JsonAlias("m_aircraftAttackCooldown")
    private Float aircraftAttackCooldown;

    @JsonAlias("m_aircraftSpeed")
    private Float aircraftSpeed;

    @JsonAlias("m_aircraftAmmo")
    private Integer aircraftAmmo;

    @JsonAlias("m_aircraftDetectionRadius")
    private Float aircraftDetectionRadius;

    @JsonAlias("m_aircraftAvoidanceRadius")
    private Float aircraftAvoidanceRadius;
}
