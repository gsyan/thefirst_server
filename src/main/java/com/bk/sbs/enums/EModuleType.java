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
    beam(2),
    missile(3),
    hanger(4),
    shield(5),
    interceptor(6),
    max(7);

    private final int value;

    EModuleType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static EModuleType fromValue(int value) {
        for (EModuleType type : values()) {
            if (type.value == value) return type;
        }
        return none;
    }

    @JsonCreator
    public static EModuleType fromJson(String value) {
        for (EModuleType type : values()) {
            if (type.name().equals(value)) return type;
        }
        try {
            int intVal = Integer.parseInt(value);
            for (EModuleType type : values()) {
                if (type.value == intVal) return type;
            }
        } catch (NumberFormatException e) { /* ignore */ }
        return none;
    }
}