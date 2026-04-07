package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 캐릭터별 클리어된 존 목록 — 각 존은 독립적, 순서 무관. isRestored=true면 적 수복 상태 (수확/입장 제외)
@Entity
@Table(name = "cleared_zone",
        uniqueConstraints = @UniqueConstraint(columnNames = {"character_id", "zone_name"}))
@Getter
@Setter
@NoArgsConstructor
public class ClearedZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(nullable = false)
    private Instant clearedAt = Instant.now();

    // 적 수복 여부 — true이면 수확/랭킹 집계에서 제외, 재입장 가능
    @Column(nullable = false)
    private boolean isRestored = false;

    // 수복된 시각 (관리 목적)
    private Instant restoredAt;

    public ClearedZone(Long characterId, String zoneName) {
        this.characterId = characterId;
        this.zoneName = zoneName;
        this.clearedAt = Instant.now();
        this.isRestored = false;
    }
}
