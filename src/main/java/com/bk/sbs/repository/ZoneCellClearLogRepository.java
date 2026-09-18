package com.bk.sbs.repository;

import com.bk.sbs.entity.ZoneCellClearLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ZoneCellClearLogRepository extends JpaRepository<ZoneCellClearLog, Long> {

    List<ZoneCellClearLog> findByZoneRunIdOrderByClearedAtAsc(Long zoneRunId);

    // 같은 셀을 재방문 파밍으로 여러 번 클리어할 수 있으므로, 카드 선택 확정 시 "그 셀의 가장 최근 클리어 로그"를 찾아야 함
    java.util.Optional<ZoneCellClearLog> findTopByZoneRunIdAndCellOrderByClearedAtDesc(Long zoneRunId, String cell);

    // 이 런에서 셀을 하나라도 클리어했는지 — 하나도 없으면 "진행 없는 런"으로 간주(다른 존 충돌 판정에 사용)
    boolean existsByZoneRunId(Long zoneRunId);

    // 업적(ZoneFullClear) — 이 런에서 서로 다른 셀을 몇 개 클리어했는지(같은 셀 재방문 파밍은 중복 제외)
    @Query("SELECT COUNT(DISTINCT l.cell) FROM ZoneCellClearLog l WHERE l.zoneRunId = :zoneRunId")
    long countDistinctCellByZoneRunId(@Param("zoneRunId") Long zoneRunId);

    // 업적(CellClear) — 커맨더 전체 런 통틀어 일반(비이벤트) 셀 누적 클리어수. 로그는 영구 보관되므로 이 COUNT가 곧 lifetime 총합
    @Query("SELECT COUNT(l) FROM ZoneCellClearLog l WHERE l.zoneRunId IN (SELECT r.id FROM ZoneRun r WHERE r.commanderId = :commanderId) AND l.treasureRewardType IS NULL")
    long countNormalCellClearByCommanderId(@Param("commanderId") Long commanderId);

    // 업적(EventCell) — 커맨더 전체 런 통틀어 트레저 보상 종류를 가리지 않고 클리어한 이벤트 셀 누적 개수
    @Query("SELECT COUNT(l) FROM ZoneCellClearLog l WHERE l.zoneRunId IN (SELECT r.id FROM ZoneRun r WHERE r.commanderId = :commanderId) AND l.treasureRewardType IS NOT NULL")
    long countEventCellClearByCommanderId(@Param("commanderId") Long commanderId);

    // 일일 업적(CellClear) — 지정 구간(오늘 UTC) 안에서만 일반(비이벤트) 셀 클리어 개수
    @Query("SELECT COUNT(l) FROM ZoneCellClearLog l WHERE l.zoneRunId IN (SELECT r.id FROM ZoneRun r WHERE r.commanderId = :commanderId) AND l.treasureRewardType IS NULL AND l.clearedAt >= :dayStart AND l.clearedAt < :dayEnd")
    long countNormalCellClearByCommanderIdAndClearedAtBetween(@Param("commanderId") Long commanderId, @Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);

    // 일일 업적(EventCell) — 지정 구간(오늘 UTC) 안에서 트레저 보상 종류를 가리지 않고 클리어한 이벤트 셀 개수
    @Query("SELECT COUNT(l) FROM ZoneCellClearLog l WHERE l.zoneRunId IN (SELECT r.id FROM ZoneRun r WHERE r.commanderId = :commanderId) AND l.treasureRewardType IS NOT NULL AND l.clearedAt >= :dayStart AND l.clearedAt < :dayEnd")
    long countEventCellClearByCommanderIdAndClearedAtBetween(@Param("commanderId") Long commanderId, @Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);
}
