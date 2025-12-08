package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleHangerSubType
 * Auto-generated from Unity C# EModuleHangerSubType enum
 */
public enum EModuleHangerSubType {
    None(0),
    Standard(1),
    Advanced(2),
    Military(3);

    private final int value;

    EModuleHangerSubType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleHangerSubType fromValue(int value) {
        for (EModuleHangerSubType type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}