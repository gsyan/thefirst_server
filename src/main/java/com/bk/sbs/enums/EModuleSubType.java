package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSubType
 * Auto-generated from Unity C# EModuleSubType enum
 */
public enum EModuleSubType {
    none(0),
    body_t1_std(1001),
    body_t1_adv(1002),
    engine_t1_std(2001),
    engine_t1_adv(2002),
    beam_t1_std(3001),
    beam_t1_adv(3002),
    missile_t1_std(4001),
    missile_t1_adv(4002),
    hanger_t1_std(5001),
    hanger_t1_adv(5002);

    private final int value;

    EModuleSubType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static EModuleSubType fromValue(int value) {
        for (EModuleSubType type : values()) {
            if (type.value == value) return type;
        }
        return none;
    }
}