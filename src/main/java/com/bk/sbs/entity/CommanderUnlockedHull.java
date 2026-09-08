//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 커맨더가 업적포인트로 언락 완료한 함체(티어4+) 기록 — 티어1~3은 언락 불필요라 여기 없어도 사용 가능
@Entity
@Table(name = "commander_unlocked_hull")
@Getter
@Setter
@NoArgsConstructor
public class CommanderUnlockedHull {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "hull_sub_type", nullable = false, length = 100)
    private String hullSubType;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt = Instant.now();

    public CommanderUnlockedHull(Long commanderId, String hullSubType) {
        this.commanderId = commanderId;
        this.hullSubType = hullSubType;
        this.unlockedAt = Instant.now();
    }
}
