package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSubType
 * Auto-generated from Unity C# EModuleSubType enum
 */
public enum EModuleSubType {
    none(0),
    h1_11100(10111100),
    h1_11110(10111110),
    h1_21100(10121100),
    h1_22100(10122100),
    h1_22200(10122200),
    h1_32200(10132200),
    h1_33200(10133200),
    h1_33300(10133300),
    h1_43300(10143300),
    h1_44300(10144300),
    h1_44400(10144400),
    h1_54400(10154400),
    h1_55400(10155400),
    beam1(20100000),
    missile1(30100000),
    hangar1(40100000),
    shield1(50100000),
    interceptor1(60100000);

    private final int value;

    EModuleSubType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static EModuleSubType fromValue(int value) {
        for (EModuleSubType type : values()) {
            if (type.value == value) return type;
        }
        return none;
    }

    @JsonCreator
    public static EModuleSubType fromJson(String value) {
        for (EModuleSubType type : values()) {
            if (type.name().equals(value)) return type;
        }
        try {
            int intVal = Integer.parseInt(value);
            for (EModuleSubType type : values()) {
                if (type.value == intVal) return type;
            }
        } catch (NumberFormatException e) { /* ignore */ }
        return none;
    }
}