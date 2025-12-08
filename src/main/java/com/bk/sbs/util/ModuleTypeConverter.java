package com.bk.sbs.util;

import com.bk.sbs.enums.*;

public class ModuleTypeConverter {
    private static final int TYPE_SHIFT = 24;
    private static final int SUBTYPE_SHIFT = 16;
    private static final int STYLE_SHIFT = 8;
    private static final int MASK = 0xFF;

    public static int pack(EModuleType type, int subTypeValue, EModuleStyle style) {
        return ((type.ordinal()) << TYPE_SHIFT)
             | (subTypeValue << SUBTYPE_SHIFT)
             | ((style.ordinal()) << STYLE_SHIFT);
    }

    public static int packBody(EModuleBodySubType subType, EModuleStyle style) {
        return pack(EModuleType.Body, subType.getValue(), style);
    }

    public static int packWeapon(EModuleWeaponSubType subType, EModuleStyle style) {
        return pack(EModuleType.Weapon, subType.getValue(), style);
    }

    public static int packEngine(EModuleEngineSubType subType, EModuleStyle style) {
        return pack(EModuleType.Engine, subType.getValue(), style);
    }

    public static int packHanger(EModuleHangerSubType subType, EModuleStyle style) {
        return pack(EModuleType.Hanger, subType.getValue(), style);
    }

    public static EModuleType getType(int packed) {
        int typeOrdinal = (packed >> TYPE_SHIFT) & MASK;
        return EModuleType.values()[typeOrdinal];
    }

    public static int getSubTypeValue(int packed) {
        return (packed >> SUBTYPE_SHIFT) & MASK;
    }

    public static EModuleStyle getStyle(int packed) {
        int styleOrdinal = (packed >> STYLE_SHIFT) & MASK;
        return EModuleStyle.values()[styleOrdinal];
    }
}
