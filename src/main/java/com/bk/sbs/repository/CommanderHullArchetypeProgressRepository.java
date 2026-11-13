package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderHullArchetypeProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommanderHullArchetypeProgressRepository extends JpaRepository<CommanderHullArchetypeProgress, Long> {
    List<CommanderHullArchetypeProgress> findByCommanderId(Long commanderId);

    List<CommanderHullArchetypeProgress> findByCommanderIdAndHullArchetype(Long commanderId, String hullArchetype);
}
