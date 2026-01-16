package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleType
 * Auto-generated from Unity C# EModuleType enum
 */
public enum EModuleType {
    None(0),
    Body(1),
    Engine(2),
    Beam(3),
    Missile(4),
    Hanger(5),
    Max(6);

    private final int value;

    EModuleType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleType fromValue(int value) {
        for (EModuleType type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}