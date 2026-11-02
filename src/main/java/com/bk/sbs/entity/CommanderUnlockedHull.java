//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 커맨더가 보유(언락)한 함체 기록 — 티어2+ 함체의 배치 가능 여부는 비용(ap_cost)과 무관하게 전적으로 이 기록의 존재 여부로만 판정한다.
// 티어1(기본 지급 함체)은 이 기록 자체가 필요 없음 — FleetService가 코드에서 직접 "티어1이면 통과" 로 처리
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
