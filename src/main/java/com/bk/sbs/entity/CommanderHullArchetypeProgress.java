//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 커맨더-아키타입-패시브 단계별 해금 진행도 — (지휘관, 아키타입, 단계)마다 1행
// counterType은 그 단계가 세는 카운터 이름(DataTableHullArchetypePassive.counter_type), counterValue는 이 단계 안에서의 진행도,
// completed는 해금 여부(한 번 true가 되면 영구)
@Entity
@Table(name = "commander_hull_archetype_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"commander_id", "hull_archetype", "stage"}))
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

    @Column(name = "stage", nullable = false)
    private int stage;

    @Column(name = "counter_type", nullable = false, length = 50)
    private String counterType;

    @Column(name = "counter_value", nullable = false)
    private long counterValue;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    public CommanderHullArchetypeProgress(Long commanderId, String hullArchetype, int stage, String counterType) {
        this.commanderId = commanderId;
        this.hullArchetype = hullArchetype;
        this.stage = stage;
        this.counterType = counterType;
        this.counterValue = 0L;
        this.completed = false;
    }
}
