package com.bk.sbs.entity;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Ship에 유저가 실제로 장착한 모듈 1개 — row 존재 = 장착, 없음 = 미장착(장착 on/off + 공격력 강화 포인트 지원, 티어 선택 없음)
// moduleType: beam/missile/hangar만 취급(실드/요격체는 현재 바디에 슬롯 자체가 없어 미사용)
@Entity
@Getter
@Setter
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleType moduleType;

    @Column(nullable = false)
    private int slotIndex; // 같은 moduleType 안에서의 슬롯 인덱스(0부터) — modules_in_preset.csv의 등장 순서와 동일 개념

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleSubType moduleSubType;

    @Column(nullable = false)
    private int attackPoints; // 빔/미사일 공격력, 격납고는 대함 공격력 강화 포인트(1p=지휘력 1)

    @Column(nullable = false)
    private int attackToFighterPoints; // 격납고 전용 대전투기 공격력 강화 포인트 — beam/missile은 항상 0
}
