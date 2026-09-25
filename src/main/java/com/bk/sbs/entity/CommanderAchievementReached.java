//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 함대 구성 기반 업적(HullTierCount/ModuleTierCount)의 달성 기록 — 한번 달성하면 이후 장비를 바꿔도 영구 유지
@Entity
@Table(name = "commander_achievement_reached")
@Getter
@Setter
@NoArgsConstructor
public class CommanderAchievementReached {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "achievement_id", nullable = false, length = 100)
    private String achievementId;

    @Column(name = "reached_at", nullable = false)
    private Instant reachedAt = Instant.now();

    public CommanderAchievementReached(Long commanderId, String achievementId) {
        this.commanderId = commanderId;
        this.achievementId = achievementId;
        this.reachedAt = Instant.now();
    }
}
