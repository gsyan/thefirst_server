package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSlotType
 * Auto-generated from Unity C# EModuleSlotType enum
 */
public enum EModuleSlotType {
    All(0),
    Head(1),
    Rear(2),
    Side(3);

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