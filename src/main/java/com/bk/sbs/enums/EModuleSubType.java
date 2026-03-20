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
    engine_t1_std_ver1(2010101),
    engine_t1_adv_ver1(2010201),
    beam_t1_std_ver1(3010101),
    beam_t1_adv_ver1(3010201),
    missile_t1_std_ver1(4010101),
    missile_t1_adv_ver1(4010201),
    hanger_t1_std_ver1(5010101),
    hanger_t1_adv_ver1(5010201);

    private final int value;

    EModuleSubType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    // JSON 직렬화: enum 이름(String)으로 출력 — 정수값 변경에 독립적
    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static EModuleSubType fromName(String name) {
        for (EModuleSubType type : values()) {
            if (type.name().equals(name)) return type;
        }
        // 정수 문자열("1010101" 등) fallback
        try {
            return fromValue(Integer.parseInt(name));
        } catch (NumberFormatException ignored) {}
        return none;
    }

    public static EModuleSubType fromValue(int value) {
        for (EModuleSubType type : values()) {
            if (type.value == value) return type;
        }
        return none;
    }
}