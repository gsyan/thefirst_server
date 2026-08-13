package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSubType
 * Auto-generated from Unity C# EModuleSubType enum
 */
public enum EModuleSubType {
    none(0),
    body_t1_m111(101111),
    body_t1_m211(101211),
    body_t1_m221(101221),
    body_t1_m222(101222),
    body_t1_m322(101322),
    body_t1_m332(101332),
    body_t1_m333(101333),
    body_t1_m433(101433),
    body_t1_m443(101443),
    body_t1_m444(101444),
    body_t1_m544(101544),
    body_t1_m554(101554),
    beam_t1(201000),
    missile_t1(301000),
    hangar_t1(401000),
    shield_t1(501000),
    interceptor_t1(601000);

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