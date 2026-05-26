package com.bk.sbs.repository;

import com.bk.sbs.entity.ClearedZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClearedZoneRepository extends JpaRepository<ClearedZone, Long> {

    List<ClearedZone> findByCharacterId(Long characterId);

    boolean existsByCharacterIdAndZoneName(Long characterId, String zoneName);

    java.util.Optional<ClearedZone> findByCharacterIdAndZoneName(Long characterId, String zoneName);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE ClearedZone cz SET cz.rewardClaimed = false, cz.clearedAt = CURRENT_TIMESTAMP WHERE cz.characterId = :characterId AND cz.zoneName = :zoneName")
    void resetRewardClaimed(@org.springframework.data.repository.query.Param("characterId") Long characterId,
                            @org.springframework.data.repository.query.Param("zoneName") String zoneName);

    @Query("SELECT DISTINCT cz.characterId FROM ClearedZone cz")
    List<Long> findAllCharacterIdsWithAnyCleared();

    @Query("SELECT cz.zoneName FROM ClearedZone cz WHERE cz.characterId = :characterId ORDER BY cz.clearedAt ASC")
    List<String> findZoneNamesByCharacterId(@Param("characterId") Long characterId);
}
