package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleBodySubType;
import com.bk.sbs.enums.EModuleStyle;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ModuleBodyDataDto {
    @JsonAlias("m_moduleTypePacked")
    private Integer moduleTypePacked;

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

    @JsonAlias("m_cargoCapacity")
    private float cargoCapacity;

    @JsonAlias("m_upgradeCost")
    private CostStruct upgradeCost;

    @JsonAlias("m_description")
    private String description;

    public EModuleBodySubType getModuleSubType() {
        for (EModuleBodySubType type : EModuleBodySubType.values())
            if (type.getValue() == subType) return type;
        return EModuleBodySubType.None;
    }

    public EModuleStyle getModuleStyle() {
        for (EModuleStyle style : EModuleStyle.values())
            if (style.getValue() == this.style) return style;
        return EModuleStyle.None;
    }
}
