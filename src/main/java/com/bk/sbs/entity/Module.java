package com.bk.sbs.entity;

import com.bk.sbs.enums.EModuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Ship에 유저가 실제로 장착한 모듈 1개 — row 존재 = 장착, 없음 = 미장착(장착 on/off + 티어 선택 + 강화 포인트 지원)
// moduleType: beam/missile/hangar는 슬롯별 1행, shield/interceptor는 함선당 최대 1행(slotIndex 항상 0)
// attackPoints/attackToFighterPoints는 카테고리별 의미가 다름 — shield: 게이지/회복속도, interceptor: 회복속도/0, hangar: 대함/대전투기, beam·missile: 공격력/0 (beam·missile 연사력=fireRatePoints, missile 침묵=silencePoints, hangar 탄약/체력/교란=ammoPoints/healthPoints/disruptPoints)
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
    private int slotIndex; // 같은 moduleType 안에서의 슬롯 인덱스(0부터)

    @Column(nullable = false)
    private String moduleSubType;

    @Column(nullable = false)
    private int attackPoints; // 빔/미사일 공격력, 격납고는 대함 공격력 강화 포인트(1p=지휘력 1)

    @Column(nullable = false)
    private int attackToFighterPoints; // 격납고 전용 대전투기 공격력 강화 포인트 — beam/missile은 항상 0

    @Column(nullable = false)
    private int fireRatePoints; // 빔/미사일 전용 연사력(쿨다운 감소) 강화 포인트 — 그 외 카테고리는 항상 0

    @Column(nullable = false)
    private int silencePoints; // 미사일 전용 침묵시간 강화 포인트 — 그 외 카테고리는 항상 0

    @Column(nullable = false)
    private int ammoPoints; // 격납고 전용 함재기 탄약 강화 포인트 — 그 외 카테고리는 항상 0

    @Column(nullable = false)
    private int healthPoints; // 격납고 전용 함재기 체력 강화 포인트 — 그 외 카테고리는 항상 0

    @Column(nullable = false)
    private int disruptPoints; // 격납고 전용 함재기 교란(명중 시 공격 딜레이) 강화 포인트 — 그 외 카테고리는 항상 0
}
