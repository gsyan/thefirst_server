package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderFleetPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommanderFleetPresetRepository extends JpaRepository<CommanderFleetPreset, Long> {

    Optional<CommanderFleetPreset> findByCommanderIdAndPresetIndex(Long commanderId, int presetIndex);

    List<CommanderFleetPreset> findByCommanderIdOrderByPresetIndex(Long commanderId);

    @Modifying
    @Query("DELETE FROM CommanderFleetPreset p WHERE p.commanderId = :commanderId")
    void deleteByCommanderId(@Param("commanderId") Long commanderId);
}
