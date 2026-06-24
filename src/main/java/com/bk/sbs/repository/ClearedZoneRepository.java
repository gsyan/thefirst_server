package com.bk.sbs.repository;

import com.bk.sbs.entity.ClearedZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClearedZoneRepository extends JpaRepository<ClearedZone, Long> {

    List<ClearedZone> findByCommanderId(Long commanderId);

    boolean existsByCommanderIdAndZoneName(Long commanderId, String zoneName);

    java.util.Optional<ClearedZone> findByCommanderIdAndZoneName(Long commanderId, String zoneName);

    List<ClearedZone> findByCommanderIdAndRewardClaimedFalse(Long commanderId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE cleared_zone SET reward_claimed = false, cleared_at = UTC_TIMESTAMP(6) WHERE commander_id = :commanderId AND zone_name = :zoneName", nativeQuery = true)
    void resetRewardClaimed(@org.springframework.data.repository.query.Param("commanderId") Long commanderId,
                            @org.springframework.data.repository.query.Param("zoneName") String zoneName);

    @Query("SELECT DISTINCT cz.commanderId FROM ClearedZone cz")
    List<Long> findAllCommanderIdsWithAnyCleared();

    @Query("SELECT cz.zoneName FROM ClearedZone cz WHERE cz.commanderId = :commanderId ORDER BY cz.clearedAt ASC")
    List<String> findZoneNamesByCommanderId(@Param("commanderId") Long commanderId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ClearedZone cz WHERE cz.commanderId = :commanderId")
    void deleteByCommanderId(@Param("commanderId") Long commanderId);
}


