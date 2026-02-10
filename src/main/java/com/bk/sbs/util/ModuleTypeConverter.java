package com.bk.sbs.util;

import com.bk.sbs.enums.*;

public class ModuleTypeConverter {
    private static final int TYPE_SHIFT = 24;
    private static final int SUBTYPE_SHIFT = 16;
    private static final int SLOTTYPE_SHIFT = 8;
    private static final int MASK = 0xFF;

    public static int pack(EModuleType type, EModuleSubType subType, EModuleSlotType slotType) {
        // SubType에서 순수한 서브타입 값만 추출 (1001 -> 1, 2002 -> 2)
        int pureSubType = subType.getValue() % 1000;
        return (type.getValue() << TYPE_SHIFT) | (pureSubType << SUBTYPE_SHIFT) | (slotType.getValue() << SLOTTYPE_SHIFT);
    }

    public static EModuleType getType(int packed) {
        int typeValue = (packed >> TYPE_SHIFT) & MASK;
        return EModuleType.fromValue(typeValue);
    }

    public static EModuleSubType getSubType(int packed) {
        int pureSubType = (packed >> SUBTYPE_SHIFT) & MASK;
        if (pureSubType == 0) return EModuleSubType.none;

        // Type 정보를 가져와서 완전한 SubType 값으로 복원 (Type=1, SubType=1 -> 1001)
        EModuleType type = getType(packed);
        int fullSubType = type.getValue() * 1000 + pureSubType;
        return EModuleSubType.fromValue(fullSubType);
    }

    public static EModuleSlotType getSlotType(int packed) {
        int slotTypeValue = (packed >> SLOTTYPE_SHIFT) & MASK;
        return EModuleSlotType.fromValue(slotTypeValue);
    }

    /**
     * 슬롯 타입 호환성 체크
     * @param moduleSlotType 모듈의 슬롯 타입
     * @param slotType 실제 슬롯의 타입
     * @return 부착 가능 여부
     */
    public static boolean canAttachToSlot(EModuleSlotType moduleSlotType, EModuleSlotType slotType) {
        // All(0) = 모든 슬롯 허용
        if (moduleSlotType == EModuleSlotType.All || slotType == EModuleSlotType.All) {
            return true;
        }

        // Side는 Head, Rear 제외한 모든 슬롯 허용
        if (moduleSlotType == EModuleSlotType.Side || slotType == EModuleSlotType.Side) {
            EModuleSlotType other = (moduleSlotType == EModuleSlotType.Side) ? slotType : moduleSlotType;
            return other != EModuleSlotType.Head && other != EModuleSlotType.Rear;
        }

        // 정확히 일치해야 부착 가능
        return moduleSlotType == slotType;
    }
}
