//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

// 커맨더가 수령 완료한 일일 업적 기록 — claimDate(UTC) 하루 동안만 유효, 날짜가 바뀌면 같은 achievementId도 재수령 가능
@Entity
@Table(name = "commander_daily_achievement_claim")
@Getter
@Setter
@NoArgsConstructor
public class CommanderDailyAchievementClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "daily_achievement_id", nullable = false, length = 100)
    private String dailyAchievementId;

    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    @Column(name = "is_vip", nullable = false)
    private boolean isVip;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt = Instant.now();

    public CommanderDailyAchievementClaim(Long commanderId, String dailyAchievementId, LocalDate claimDate, boolean isVip) {
        this.commanderId = commanderId;
        this.dailyAchievementId = dailyAchievementId;
        this.claimDate = claimDate;
        this.isVip = isVip;
        this.claimedAt = Instant.now();
    }
}
