package com.bk.sbs.repository;

import com.bk.sbs.entity.ZoneCellClearLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneCellClearLogRepository extends JpaRepository<ZoneCellClearLog, Long> {

    List<ZoneCellClearLog> findByZoneRunIdOrderByClearedAtAsc(Long zoneRunId);

    // 같은 셀을 재방문 파밍으로 여러 번 클리어할 수 있으므로, 카드 선택 확정 시 "그 셀의 가장 최근 클리어 로그"를 찾아야 함
    java.util.Optional<ZoneCellClearLog> findTopByZoneRunIdAndCellOrderByClearedAtDesc(Long zoneRunId, String cell);

    // 이 런에서 셀을 하나라도 클리어했는지 — 하나도 없으면 "진행 없는 런"으로 간주(다른 존 충돌 판정에 사용)
    boolean existsByZoneRunId(Long zoneRunId);
}
