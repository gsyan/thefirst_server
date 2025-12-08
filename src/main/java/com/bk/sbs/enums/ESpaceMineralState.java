package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ESpaceMineralState
 * Auto-generated from Unity C# ESpaceMineralState enum
 */
public enum ESpaceMineralState {
    None(0),
    Occupied(1),
    End(2),
    Max(3);

    private final int value;

    ESpaceMineralState(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static ESpaceMineralState fromValue(int value) {
        for (ESpaceMineralState type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}