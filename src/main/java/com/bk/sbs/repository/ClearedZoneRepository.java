package com.bk.sbs.repository;

import com.bk.sbs.entity.ClearedZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClearedZoneRepository extends JpaRepository<ClearedZone, Long> {

    List<ClearedZone> findByCharacterId(Long characterId);

    boolean existsByCharacterIdAndZoneName(Long characterId, String zoneName);

    @Query("SELECT DISTINCT cz.characterId FROM ClearedZone cz")
    List<Long> findAllCharacterIdsWithAnyCleared();

    @Query("SELECT cz.zoneName FROM ClearedZone cz WHERE cz.characterId = :characterId")
    List<String> findZoneNamesByCharacterId(@Param("characterId") Long characterId);
}
