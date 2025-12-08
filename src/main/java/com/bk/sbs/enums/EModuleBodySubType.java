package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleBodySubType
 * Auto-generated from Unity C# EModuleBodySubType enum
 */
public enum EModuleBodySubType {
    None(0),
    Battle(1),
    Aircraft(2),
    Scout(3),
    Repair(4);

    private final int value;

    EModuleBodySubType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleBodySubType fromValue(int value) {
        for (EModuleBodySubType type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}