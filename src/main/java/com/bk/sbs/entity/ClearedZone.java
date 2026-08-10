package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 캐릭터별 클리어된 존 목록 — clearedAt은 마지막 클리어 시간(재도전 시 갱신)
@Entity
@Table(name = "cleared_zone",
        uniqueConstraints = @UniqueConstraint(columnNames = {"commander_id", "zone_name"}))
@Getter
@Setter
@NoArgsConstructor
public class ClearedZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(nullable = false)
    private Instant clearedAt = Instant.now();

    @Column(nullable = false)
    private boolean rewardClaimed = false;      // per-run: clearZoneStage→false, claimZoneReward→true

    @Column(nullable = false)
    private boolean firstBonusClaimed = false;  // 영구: 보상 최초 지급 후 true, 리셋 없음

    public ClearedZone(Long commanderId, String zoneName) {
        this.commanderId = commanderId;
        this.zoneName = zoneName;
        this.clearedAt = Instant.now();
        this.rewardClaimed = false;
        this.firstBonusClaimed = false;
    }
}


