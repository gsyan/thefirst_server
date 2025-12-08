package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleStyle
 * Auto-generated from Unity C# EModuleStyle enum
 */
public enum EModuleStyle {
    None(0),
    StyleA(1),
    StyleB(2),
    StyleC(3),
    StyleD(4);

    private final int value;

    EModuleStyle(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleStyle fromValue(int value) {
        for (EModuleStyle type : values()) {
            if (type.value == value) return type;
        }
        return None;
    }
}