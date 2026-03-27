package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSubType
 * Auto-generated from Unity C# EModuleSubType enum
 */
public enum EModuleSubType {
    none(0),
    body_t1_std_ver1(1010101),
    body_t1_adv_ver1(1010201),
    beam_t1_std_ver1(2010101),
    beam_t1_adv_ver1(2010201),
    missile_t1_std_ver1(3010101),
    missile_t1_adv_ver1(3010201),
    hanger_t1_std_ver1(4010101),
    hanger_t1_adv_ver1(4010201);

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