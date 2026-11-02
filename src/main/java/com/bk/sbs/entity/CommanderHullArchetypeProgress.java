//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 커맨더-아키타입-카운터종류별 영구 누적치 — 아키타입 패시브(1~5성, 4단계) 언락 조건 판정용
// counterType은 아키타입별로 의미가 다른 자유 문자열(예: "destroyed_ship", "zone_run_cleared", "mineral_scavenged")
@Entity
@Table(name = "commander_hull_archetype_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"commander_id", "hull_archetype", "counter_type"}))
@Getter
@Setter
@NoArgsConstructor
public class CommanderHullArchetypeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "hull_archetype", nullable = false, length = 50)
    private String hullArchetype;

    @Column(name = "counter_type", nullable = false, length = 50)
    private String counterType;

    @Column(name = "counter_value", nullable = false)
    private long counterValue;

    public CommanderHullArchetypeProgress(Long commanderId, String hullArchetype, String counterType, long counterValue) {
        this.commanderId = commanderId;
        this.hullArchetype = hullArchetype;
        this.counterType = counterType;
        this.counterValue = counterValue;
    }
}
