package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleWeaponSubType
 * Auto-generated from Unity C# EModuleWeaponSubType enum
 */
public enum EModuleWeaponSubType {
    None(0),
    Beam(1),
    Missile(2),
    Cannon(3),
    Torpedo(4);

    private final int value;

    EModuleWeaponSubType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleWeaponSubType fromValue(int value) {
        for (EModuleWeaponSubType type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}