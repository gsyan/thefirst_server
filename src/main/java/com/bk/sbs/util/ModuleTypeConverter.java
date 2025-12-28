package com.bk.sbs.util;

import com.bk.sbs.enums.*;

public class ModuleTypeConverter {
    private static final int TYPE_SHIFT = 24;
    private static final int SUBTYPE_SHIFT = 16;
    private static final int STYLE_SHIFT = 8;
    private static final int MASK = 0xFF;

    public static int pack(EModuleType type, EModuleSubType subType, EModuleStyle style) {
        // SubType에서 순수한 서브타입 값만 추출 (1001 -> 1, 2002 -> 2)
        int pureSubType = subType.getValue() % 1000;
        return (type.getValue() << TYPE_SHIFT) | (pureSubType << SUBTYPE_SHIFT) | (style.getValue() << STYLE_SHIFT);
    }

    public static EModuleType getType(int packed) {
        int typeValue = (packed >> TYPE_SHIFT) & MASK;
        return EModuleType.fromValue(typeValue);
    }

    public static EModuleSubType getSubType(int packed) {
        int pureSubType = (packed >> SUBTYPE_SHIFT) & MASK;
        if (pureSubType == 0) return EModuleSubType.None;

        // Type 정보를 가져와서 완전한 SubType 값으로 복원 (Type=1, SubType=1 -> 1001)
        EModuleType type = getType(packed);
        int fullSubType = type.getValue() * 1000 + pureSubType;
        return EModuleSubType.fromValue(fullSubType);
    }

    public static EModuleStyle getStyle(int packed) {
        int styleValue = (packed >> STYLE_SHIFT) & MASK;
        return EModuleStyle.fromValue(styleValue);
    }
}
