package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSlotType
 * Auto-generated from Unity C# EModuleSlotType enum
 */
public enum EModuleSlotType {
    All(0),
    Head(1 << 0),
    Top(1 << 1),
    Bottom(1 << 2),
    Left(1 << 3),
    Right(1 << 4),
    Rear(1 << 5);

    private final int value;

    EModuleSlotType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleSlotType fromValue(int value) {
        for (EModuleSlotType type : values()) {
            if (type.value == value) return type;
        }
        return All;
    }
}