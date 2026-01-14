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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleSlotType moduleSlotType;

    @Column(nullable = false)
    private int moduleLevel;

    @Column(nullable = false)
    private int bodyIndex; // 어떤 body 모듈에 장착될 것인지 (Body 모듈인 경우 자신의 인덱스)

    @Column(nullable = false)
    private int slotIndex; // 함선 내에서 모듈의 슬롯 위치 (Body는 0, Weapon과 Engine은 여러 개 가능)

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime created = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();
}
