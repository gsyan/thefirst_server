//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 커맨더가 한 런 안에서 이탈 없이 존의 모든 셀(Blocked 제외)을 전부 클리어한 이력 — ZoneFullClear 업적 판정용
@Entity
@Table(name = "commander_zone_full_clear")
@Getter
@Setter
@NoArgsConstructor
public class CommanderZoneFullClear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "zone_number", nullable = false)
    private int zoneNumber;

    @Column(name = "cleared_at", nullable = false)
    private Instant clearedAt = Instant.now();

    public CommanderZoneFullClear(Long commanderId, int zoneNumber) {
        this.commanderId = commanderId;
        this.zoneNumber = zoneNumber;
        this.clearedAt = Instant.now();
    }
}
