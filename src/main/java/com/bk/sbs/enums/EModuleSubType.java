package com.bk.sbs.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EModuleSubType
 * Auto-generated from Unity C# EModuleSubType enum
 */
public enum EModuleSubType {
    none(0),
    body_t1_m1(10101),
    body_t2_m1(10201),
    body_t3_m1(10301),
    body_t4_m1(10401),
    body_t5_m1(10501),
    body_t6_m1(10601),
    body_t7_m1(10701),
    body_t8_m1(10801),
    body_t9_m1(10901),
    body_t10_m1(11001),
    body_t11_m1(11101),
    body_t12_m1(11201),
    body_t13_m1(11301),
    body_t14_m1(11401),
    beam_t1_m1(20101),
    beam_t2_m1(20201),
    beam_t3_m1(20301),
    beam_t4_m1(20401),
    beam_t5_m1(20501),
    beam_t6_m1(20601),
    beam_t7_m1(20701),
    beam_t8_m1(20801),
    beam_t9_m1(20901),
    beam_t10_m1(21001),
    beam_t11_m1(21101),
    beam_t12_m1(21201),
    beam_t13_m1(21301),
    beam_t14_m1(21401),
    missile_t1_m1(30101),
    missile_t2_m1(30201),
    missile_t3_m1(30301),
    missile_t4_m1(30401),
    missile_t5_m1(30501),
    missile_t6_m1(30601),
    missile_t7_m1(30701),
    missile_t8_m1(30801),
    missile_t9_m1(30901),
    missile_t10_m1(31001),
    missile_t11_m1(31101),
    missile_t12_m1(31201),
    missile_t13_m1(31301),
    missile_t14_m1(31401),
    hanger_t1_m1(40101),
    hanger_t2_m1(40201),
    hanger_t3_m1(40301),
    hanger_t4_m1(40401),
    hanger_t5_m1(40501),
    hanger_t6_m1(40601),
    hanger_t7_m1(40701),
    hanger_t8_m1(40801),
    hanger_t9_m1(40901),
    hanger_t10_m1(41001),
    hanger_t11_m1(41101),
    hanger_t12_m1(41201),
    hanger_t13_m1(41301),
    hanger_t14_m1(41401);

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