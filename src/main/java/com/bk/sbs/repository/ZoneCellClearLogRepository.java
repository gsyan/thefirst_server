package com.bk.sbs.repository;

import com.bk.sbs.entity.ZoneCellClearLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneCellClearLogRepository extends JpaRepository<ZoneCellClearLog, Long> {

    List<ZoneCellClearLog> findByZoneRunIdOrderByClearedAtAsc(Long zoneRunId);
}
