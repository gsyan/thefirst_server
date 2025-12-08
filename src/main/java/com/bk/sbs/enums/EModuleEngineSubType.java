package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleEngineSubType
 * Auto-generated from Unity C# EModuleEngineSubType enum
 */
public enum EModuleEngineSubType {
    None(0),
    Standard(1),
    Booster(2),
    Warp(3);

    private final int value;

    EModuleEngineSubType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleEngineSubType fromValue(int value) {
        for (EModuleEngineSubType type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}