package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleEngineSubType;
import com.bk.sbs.enums.EModuleStyle;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ModuleEngineDataDto {
    @JsonAlias("m_moduleType")
    private Integer moduleType;

    @JsonAlias("m_name")
    private String name;

    @JsonAlias("m_subType")
    private int subType;

    @JsonAlias("m_style")
    private int style;

    @JsonAlias("m_level")
    private int level;

    @JsonAlias("m_health")
    private float health;

    @JsonAlias("m_movementSpeed")
    private float movementSpeed;

    @JsonAlias("m_rotationSpeed")
    private float rotationSpeed;

    @JsonAlias("m_upgradeMoneyCost")
    private int upgradeMoneyCost;

    @JsonAlias("m_upgradeMineralCost")
    private int upgradeMineralCost;

    @JsonAlias("m_description")
    private String description;

    public EModuleEngineSubType getModuleSubType() {
        for (EModuleEngineSubType type : EModuleEngineSubType.values())
            if (type.getValue() == subType) return type;
        return EModuleEngineSubType.None;
    }

    public EModuleStyle getModuleStyle() {
        for (EModuleStyle style : EModuleStyle.values())
            if (style.getValue() == this.style) return style;
        return EModuleStyle.None;
    }
}
