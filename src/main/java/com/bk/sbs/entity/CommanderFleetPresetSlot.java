package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// CommanderFleetPreset 안의 함선 슬롯 하나. shipPresetId는 DataTableShipPreset의 preset id(바디 템플릿) 참조.
// 유저가 실제로 장착한 모듈(빔/미사일/격납고 on-off 상태)은 modules에 슬롯 단위로 별도 저장 — shipPresetId는 배치 시 기본값(빔1) 출처일 뿐, 이후엔 modules가 우선
@Entity
@Getter
@Setter
public class CommanderFleetPresetSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_preset_id", nullable = false)
    private CommanderFleetPreset fleetPreset;

    @Column(nullable = false)
    private int slotIndex;

    @Column(nullable = false)
    private String shipPresetId;

    @Column(nullable = false)
    private boolean isFront;

    @OneToMany(mappedBy = "presetSlot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CommanderFleetPresetSlotModule> modules;
}
