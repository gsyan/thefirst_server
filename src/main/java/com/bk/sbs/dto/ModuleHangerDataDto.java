package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleHangerSubType;
import com.bk.sbs.enums.EModuleStyle;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ModuleHangerDataDto {
    @JsonProperty("m_moduleTypePacked")
    private int moduleTypePacked;

    @JsonProperty("m_name")
    private String name;

    @JsonProperty("m_subType")
    private EModuleHangerSubType subType;

    @JsonProperty("m_style")
    private EModuleStyle style;

    @JsonProperty("m_level")
    private int level;

    @JsonProperty("m_health")
    private float health;

    @JsonProperty("m_hangarCapability")
    private int hangarCapability;

    @JsonProperty("m_scoutCapability")
    private int scoutCapability;

    @JsonProperty("m_launchCool")
    private float launchCool;

    @JsonProperty("m_launchCount")
    private int launchCount;

    @JsonProperty("m_maintenanceTime")
    private float maintenanceTime;

    @JsonProperty("m_aircraftHealth")
    private float aircraftHealth;

    @JsonProperty("m_aircraftAttackPower")
    private float aircraftAttackPower;

    @JsonProperty("m_aircraftAmmo")
    private int aircraftAmmo;

    @JsonProperty("m_upgradeCost")
    private CostStruct upgradeCost;

    @JsonProperty("m_description")
    private String description;
}
