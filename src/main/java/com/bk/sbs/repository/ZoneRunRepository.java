package com.bk.sbs.repository;

import com.bk.sbs.enums.EZoneRunStatus;
import com.bk.sbs.entity.ZoneRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ZoneRunRepository extends JpaRepository<ZoneRun, Long> {

    Optional<ZoneRun> findByCommanderIdAndStatus(Long commanderId, EZoneRunStatus status);
}
