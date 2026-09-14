package com.bk.sbs.repository;

import com.bk.sbs.enums.EZoneRunStatus;
import com.bk.sbs.entity.ZoneRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ZoneRunRepository extends JpaRepository<ZoneRun, Long> {

    Optional<ZoneRun> findByCommanderIdAndStatus(Long commanderId, EZoneRunStatus status);

    // 일일 업적(ZoneClearTotal) — 지정 구간(오늘 UTC) 안에서 탈출(ESCAPED)로 종료한 런 개수(최고기록 갱신 여부 무관, 재방문 파밍도 포함)
    @Query("SELECT COUNT(r) FROM ZoneRun r WHERE r.commanderId = :commanderId AND r.status = com.bk.sbs.enums.EZoneRunStatus.ESCAPED AND r.endedAt >= :dayStart AND r.endedAt < :dayEnd")
    long countEscapedByCommanderIdAndEndedAtBetween(@Param("commanderId") Long commanderId, @Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);
}
