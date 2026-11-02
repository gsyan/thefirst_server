package com.bk.sbs.entity;

import com.bk.sbs.enums.EModuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Ship에 유저가 실제로 장착한 모듈 1개 — row 존재 = 장착, 없음 = 미장착(장착 on/off + 티어 선택만 지원)
// moduleType: beam/missile/hangar는 슬롯별 1행, shield/interceptor는 함선당 최대 1행(slotIndex 항상 0)
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
}
