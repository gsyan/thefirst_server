package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderUnlockedHull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommanderUnlockedHullRepository extends JpaRepository<CommanderUnlockedHull, Long> {

    boolean existsByCommanderIdAndHullSubType(Long commanderId, String hullSubType);

    List<CommanderUnlockedHull> findByCommanderId(Long commanderId);
}
