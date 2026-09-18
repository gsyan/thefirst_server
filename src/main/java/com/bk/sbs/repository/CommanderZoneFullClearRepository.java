package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderZoneFullClear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommanderZoneFullClearRepository extends JpaRepository<CommanderZoneFullClear, Long> {

    boolean existsByCommanderIdAndZoneNumber(Long commanderId, int zoneNumber);

    List<CommanderZoneFullClear> findByCommanderId(Long commanderId);
}
