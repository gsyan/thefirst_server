package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

// 커맨더당 여러 개(PvP용, 탐사용 등) 보유 가능한 함대 프리셋. presetIndex=0이 현재 기본 활성 함대.
@Entity
@Getter
@Setter
public class CommanderFleetPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long commanderId;

    @Column(nullable = false)
    private int presetIndex;

    // 전투 중 전술 토글(체력회복/미사일/함재기) 비트마스크(bit0=수리, bit1=미사일, bit2=함재기) — 이 프리셋(함대) 편성 자체에 귀속된 설정값
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int tacticOptions = 0;

    @Column(nullable = false)
    private Instant created = Instant.now();

    @Column(nullable = false)
    private Instant modified = Instant.now();

    @OneToMany(mappedBy = "fleetPreset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CommanderFleetPresetSlot> slots;
}
