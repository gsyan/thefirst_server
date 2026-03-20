package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleType
 * Auto-generated from Unity C# EModuleType enum
 */
public enum EModuleType {
    none(0),
    body(1),
    engine(2),
    beam(3),
    missile(4),
    hanger(5),
    max(6);

    private final int value;

    EModuleType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static EModuleType fromName(String name) {
        for (EModuleType type : values()) {
            if (type.name().equals(name)) return type;
        }
        // 정수 문자열("3" 등) fallback
        try {
            return fromValue(Integer.parseInt(name));
        } catch (NumberFormatException ignored) {}
        return none;
    }

    public static EModuleType fromValue(int value) {
        for (EModuleType type : values()) {
            if (type.value == value) return type;
        }
        return none;
    }
}