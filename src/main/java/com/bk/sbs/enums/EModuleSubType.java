package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSubType
 * Auto-generated from Unity C# EModuleSubType enum
 */
public enum EModuleSubType {
    None(0),
    Body_Battle(1001),
    Body_Aircraft(1002),
    Body_Repair(1003),
    Engine_Standard(2001),
    Engine_Booster(2002),
    Engine_Warp(2003),
    Beam_Standard(3001),
    Beam_Advanced(3002),
    Missile_Standard(4001),
    Missile_Advanced(4002),
    Hanger_Standard(5001),
    Hanger_Advanced(5002);

    private final int value;

    EModuleSubType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleSubType fromValue(int value) {
        for (EModuleSubType type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}