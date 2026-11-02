package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderArchetypeModuleUpgrade;
import com.bk.sbs.enums.EModuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommanderArchetypeModuleUpgradeRepository extends JpaRepository<CommanderArchetypeModuleUpgrade, Long> {
    List<CommanderArchetypeModuleUpgrade> findByCommanderId(Long commanderId);

    Optional<CommanderArchetypeModuleUpgrade> findByCommanderIdAndHullArchetypeAndModuleTypeAndSlotIndex(
            Long commanderId, String hullArchetype, EModuleType moduleType, int slotIndex);
}
