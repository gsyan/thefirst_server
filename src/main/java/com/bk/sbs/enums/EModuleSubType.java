package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSubType
 * Auto-generated from Unity C# EModuleSubType enum
 */
public enum EModuleSubType {
    none(0),
    body_battle(1001),
    body_aircraft(1002),
    body_repair(1003),
    engine_standard(2001),
    engine_booster(2002),
    engine_warp(2003),
    beam_standard(3001),
    beam_advanced(3002),
    missile_standard(4001),
    missile_advanced(4002),
    hanger_standard(5001),
    hanger_advanced(5002);

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