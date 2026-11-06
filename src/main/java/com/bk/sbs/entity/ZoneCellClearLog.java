package com.bk.sbs.entity;

import com.bk.sbs.enums.ETreasureRewardType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// ZoneRun 하나 안에서 셀을 클리어한 순서/시각 기록 — 재접속 시 진행 복구/진단용(그리드 자체는 고정 레이아웃이라 좌표만 있으면 재현 가능)
@Entity
@Table(name = "zone_cell_clear_log")
@Getter
@Setter
@NoArgsConstructor
public class ZoneCellClearLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_run_id", nullable = false)
    private Long zoneRunId;

    // "row-col" 형식(0-indexed) — ZoneRun.currentCell과 동일 규칙. DB/네트워크/에디터 전부 0-indexed로 통일
    @Column(nullable = false, length = 20)
    private String cell;

    @Column(nullable = false)
    private Instant clearedAt = Instant.now();

    // Treasure(Event) 셀 클리어 시 당첨된 보상 종류 — Treasure 셀이 아니면 null. getPendingHealthHealBonus()가 ShipHealthHeal 당첨 여부 확인에 사용
    @Enumerated(EnumType.STRING)
    @Column(name = "treasure_reward_type", length = 32)
    private ETreasureRewardType treasureRewardType;

    public ZoneCellClearLog(Long zoneRunId, int cellRow, int cellCol) {
        this.zoneRunId = zoneRunId;
        this.cell = cellRow + "-" + cellCol;
        this.clearedAt = Instant.now();
    }

    public int getRow() {
        return Integer.parseInt(cell.split("-")[0]);
    }

    public int getCol() {
        return Integer.parseInt(cell.split("-")[1]);
    }
}
