package com.bk.sbs.entity;

import com.bk.sbs.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ShipModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleType moduleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleSubType moduleSubType;

    @Column(nullable = false)
    private int moduleLevel;

    @Column(nullable = false)
    private int bodyIndex; // 어떤 body 모듈에 장착될 것인지 (Body 모듈인 경우 자신의 인덱스)

    @Column(nullable = false)
    private int slotIndex; // 함선 내에서 모듈의 슬롯 위치

    // 이 슬롯에 투자한 modulePoint 이력 — 리셋 시 100% 환급
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int investedModulePoint = 0;

    // 모듈포인트 기준값 — 미네랄 초기화 시 이 값으로 복귀
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(100) DEFAULT 'none'")
    private EModuleSubType modulePointSubType = EModuleSubType.none;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int modulePointLevel = 0;

    // 투자한 미네랄 이력 — 전투 승리 시 소모 + 초기화, 그 전까지 환급 가능
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int investedMineral = 0;

    // 현재 체력 (절대값). body 모듈 전용. 생성 시 maxHealth로 초기화
    @Column(nullable = false, columnDefinition = "FLOAT DEFAULT 0")
    private float currentHealth = 0f;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime created = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();
}
