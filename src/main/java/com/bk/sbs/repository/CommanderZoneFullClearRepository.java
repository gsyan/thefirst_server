package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderZoneFullClear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommanderZoneFullClearRepository extends JpaRepository<CommanderZoneFullClear, Long> {

    boolean existsByCommanderIdAndZoneNumber(Long commanderId, int zoneNumber);

    List<CommanderZoneFullClear> findByCommanderId(Long commanderId);

    // 존1~zoneNumber 구간 중 완벽클리어 기록이 있는 존 개수 — (commanderId, zoneNumber) 중복 insert는 ExplorationService에서 막아 개수 == 존 수
    long countByCommanderIdAndZoneNumberLessThanEqual(Long commanderId, int zoneNumber);
}
