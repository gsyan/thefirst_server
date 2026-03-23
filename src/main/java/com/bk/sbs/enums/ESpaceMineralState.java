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

    public static ESpaceMineralState fromValue(int value) {
        for (ESpaceMineralState type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }

    @JsonCreator
    public static ESpaceMineralState fromJson(String value) {
        for (ESpaceMineralState type : values()) {
            if (type.name().equals(value)) return type;
        }
        try {
            int intVal = Integer.parseInt(value);
            for (ESpaceMineralState type : values()) {
                if (type.value == intVal) return type;
            }
        } catch (NumberFormatException e) { /* ignore */ }
        return None;
    }
}