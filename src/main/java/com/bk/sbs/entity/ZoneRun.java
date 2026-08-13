package com.bk.sbs.entity;

import com.bk.sbs.enums.EZoneRunStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 커맨더의 탐사 존 진행(런) 1개 — 전체 존을 통틀어 커맨더당 IN_PROGRESS 상태는 항상 1개뿐이어야 함(ExplorationService에서 보장)
// status(무슨 일이 일어났는지)와 rewardClaimed(보상 지급 여부)를 분리 — ClearedZone.rewardClaimed와 동일 원칙
@Entity
@Table(name = "zone_run")
@Getter
@Setter
@NoArgsConstructor
public class ZoneRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "zone_number", nullable = false)
    private int zoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EZoneRunStatus status = EZoneRunStatus.IN_PROGRESS;

    @Column(nullable = false)
    private boolean rewardClaimed = false;

    @Column(nullable = false)
    private int explorationPointBanked = 0; // 이 런에서 셀 클리어로 쌓인 미확정 탐험 포인트

    @Column(nullable = false)
    private int commanderExpBanked = 0; // 이 런에서 셀 클리어로 쌓인 미확정 지휘관 경험치

    // "row-col" 형식(0-indexed) 단일 문자열 — 마지막으로 클리어한 셀(승리 시에만 갱신), 인접 검증 기준점. 정수 2컬럼 대신 사람이 읽기 좋은 하나의 컬럼으로 저장
    // ZoneCellClearLog.cell과 동일하게 0-indexed — DB/네트워크/코드 전부 0-indexed로 통일(변환 지점을 아예 없앰)
    @Column(name = "current_cell", nullable = false, length = 20)
    private String currentCell;

    // 마지막 셀 클리어 시점의 내 함대 체력 비율(슬롯 포지션 인덱스별) JSON — 재접속 시 손상 상태 복구용. 없으면 null(만피로 스폰된 상태 그대로)
    @Column(name = "fleet_health_snapshot_json", columnDefinition = "TEXT")
    private String fleetHealthSnapshotJson;

    // enter-cell 없이 clear-cell만 반복 호출해 보상을 무한 획득하는 것을 막기 위한 1회용 챌린지 토큰 — enter-cell 발급, clear-cell 검증 후 즉시 무효화
    @Column(name = "active_challenge_token", length = 36)
    private String activeChallengeToken;

    // 토큰이 발급된 셀("row-col") — clear-cell 요청 좌표와 일치해야 함
    @Column(name = "active_challenge_cell", length = 20)
    private String activeChallengeCell;

    // 토큰 발급 시각 — clear-cell과의 최소 경과시간 검증 및 향후 전투시간 상한 계산(S4)에 사용
    @Column(name = "active_challenge_issued_at")
    private Instant activeChallengeIssuedAt;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant endedAt; // ESCAPED/ABANDONED로 종결된 시각 — IN_PROGRESS면 null

    public ZoneRun(Long commanderId, int zoneNumber, int startRow, int startCol) {
        this.commanderId = commanderId;
        this.zoneNumber = zoneNumber;
        this.status = EZoneRunStatus.IN_PROGRESS;
        this.rewardClaimed = false;
        this.explorationPointBanked = 0;
        this.commanderExpBanked = 0;
        setCurrentPosition(startRow, startCol);
        this.startedAt = Instant.now();
    }

    public int getCurrentRow() {
        return Integer.parseInt(currentCell.split("-")[0]);
    }

    public int getCurrentCol() {
        return Integer.parseInt(currentCell.split("-")[1]);
    }

    public void setCurrentPosition(int row, int col) {
        this.currentCell = row + "-" + col;
    }
}
