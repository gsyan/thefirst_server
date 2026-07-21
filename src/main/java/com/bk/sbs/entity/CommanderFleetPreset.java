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

    @Column(nullable = false)
    private Instant created = Instant.now();

    @Column(nullable = false)
    private Instant modified = Instant.now();

    @OneToMany(mappedBy = "fleetPreset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CommanderFleetPresetSlot> slots;
}
