package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderHullArchetypeProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommanderHullArchetypeProgressRepository extends JpaRepository<CommanderHullArchetypeProgress, Long> {
    List<CommanderHullArchetypeProgress> findByCommanderId(Long commanderId);

    Optional<CommanderHullArchetypeProgress> findByCommanderIdAndHullArchetypeAndCounterType(
            Long commanderId, String hullArchetype, String counterType);
}
