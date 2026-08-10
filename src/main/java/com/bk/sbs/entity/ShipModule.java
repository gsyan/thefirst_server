package com.bk.sbs.entity;

import com.bk.sbs.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

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

    // 현재 체력 (절대값). body 모듈 전용. 생성 시 maxHealth로 초기화
    @Column(nullable = false, columnDefinition = "FLOAT DEFAULT 0")
    private float currentHealth = 0f;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private Instant created = Instant.now();

    @Column(nullable = false)
    private Instant modified = Instant.now();
}
