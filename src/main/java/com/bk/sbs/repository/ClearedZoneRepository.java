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

    // 수확/입장 가능한 존 이름 목록 (isRestored=false인 것만)
    @Query("SELECT cz.zoneName FROM ClearedZone cz WHERE cz.characterId = :characterId AND cz.isRestored = false")
    List<String> findZoneNamesByCharacterId(@Param("characterId") Long characterId);

    // best score 계산용 — isRestored 무관 전체 (랭킹 기록 보장)
    @Query("SELECT cz.zoneName FROM ClearedZone cz WHERE cz.characterId = :characterId")
    List<String> findAllZoneNamesByCharacterId(@Param("characterId") Long characterId);

    // 수복 대상 후보 — isRestored=false이고 zone group >= 2인 존 엔티티 목록
    @Query("SELECT cz FROM ClearedZone cz WHERE cz.characterId = :characterId AND cz.isRestored = false")
    List<ClearedZone> findActiveByCharacterId(@Param("characterId") Long characterId);
}
