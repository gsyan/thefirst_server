package com.bk.sbs.entity;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// CommanderFleetPresetSlot에 유저가 실제로 장착한 모듈 1개 — row 존재 = 장착, 없음 = 미장착(on/off만 지원, 티어 선택 없음)
// moduleType: beam/missile/hangar만 취급(실드/요격체는 현재 바디에 슬롯 자체가 없어 미사용)
@Entity
@Getter
@Setter
public class CommanderFleetPresetSlotModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preset_slot_id", nullable = false)
    private CommanderFleetPresetSlot presetSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleType moduleType;

    @Column(nullable = false)
    private int slotIndex; // 같은 moduleType 안에서의 슬롯 인덱스(0부터) — modules_in_preset.csv의 등장 순서와 동일 개념

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleSubType moduleSubType;
}
