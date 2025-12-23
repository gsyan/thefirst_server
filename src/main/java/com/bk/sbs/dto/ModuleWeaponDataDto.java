package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleWeaponSubType;
import com.bk.sbs.enums.EModuleStyle;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ModuleWeaponDataDto {
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

    @JsonAlias("m_attackFireCount")
    private int attackFireCount;

    @JsonAlias("m_attackPower")
    private float attackPower;

    @JsonAlias("m_attackCoolTime")
    private float attackCoolTime;

    @JsonAlias("m_projectileLength")
    private Float projectileLength;

    @JsonAlias("m_projectileWidth")
    private Float projectileWidth;

    @JsonAlias("m_projectileSpeed")
    private Float projectileSpeed;

    @JsonAlias("m_upgradeCost")
    private CostStruct upgradeCost;

    @JsonAlias("m_description")
    private String description;

    public EModuleWeaponSubType getModuleSubType() {
        for (EModuleWeaponSubType type : EModuleWeaponSubType.values())
            if (type.getValue() == subType) return type;
        return EModuleWeaponSubType.None;
    }

    public EModuleStyle getModuleStyle() {
        for (EModuleStyle style : EModuleStyle.values())
            if (style.getValue() == this.style) return style;
        return EModuleStyle.None;
    }
}
