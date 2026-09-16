//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 커맨더가 수령 완료한 업적 기록 — 한번 수령하면 영구 유지(같은 achievementId 재수령 불가)
@Entity
@Table(name = "commander_achievement_claim")
@Getter
@Setter
@NoArgsConstructor
public class CommanderAchievementClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "achievement_id", nullable = false, length = 100)
    private String achievementId;

    @Column(name = "is_vip", nullable = false)
    private boolean isVip;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt = Instant.now();

    public CommanderAchievementClaim(Long commanderId, String achievementId, boolean isVip) {
        this.commanderId = commanderId;
        this.achievementId = achievementId;
        this.isVip = isVip;
        this.claimedAt = Instant.now();
    }
}
