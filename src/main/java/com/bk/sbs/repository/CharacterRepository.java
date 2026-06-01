//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.ClearedZone;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<Character, Long> {
    boolean existsByCharacterName(String characterName);
    Optional<Character> findById(Long id);
    List<Character> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Character c WHERE c.id = :id")
    Optional<Character> findByIdForUpdate(@Param("id") Long id);

    // Zone 랭킹 서버 시작 시 동기화 - cleared_zone 테이블에 1개 이상 존 있는 캐릭터 조회
    @Query("SELECT c FROM Character c WHERE c.deleted = false AND c.id IN (SELECT cz.characterId FROM ClearedZone cz)")
    List<Character> findAllWithClearedZone();

    // lastOnlineAt 갱신 — threshold보다 오래됐을 때만 업데이트 (30s 스로틀링)
    @Modifying
    @Query("UPDATE Character c SET c.lastOnlineAt = :now WHERE c.id = :id AND (c.lastOnlineAt IS NULL OR c.lastOnlineAt < :threshold)")
    int updateLastOnlineAtIfStale(@Param("id") Long id, @Param("now") Instant now, @Param("threshold") Instant threshold);

    @Modifying
    @Query("DELETE FROM Character c WHERE c.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") Long accountId);
}
