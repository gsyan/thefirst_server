//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.ClearedZone;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommanderRepository extends JpaRepository<Commander, Long> {
    boolean existsByCommanderName(String commanderName);
    Optional<Commander> findById(Long id);
    List<Commander> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Commander c WHERE c.id = :id")
    Optional<Commander> findByIdForUpdate(@Param("id") Long id);

    // Zone 랭킹 서버 시작 시 동기화 - cleared_zone 테이블에 1개 이상 존 있는 커맨더 조회
    @Query("SELECT c FROM Commander c WHERE c.deleted = false AND c.id IN (SELECT cz.commanderId FROM ClearedZone cz)")
    List<Commander> findAllWithClearedZone();

    // lastOnlineAt 갱신 — threshold보다 오래됐을 때만 업데이트 (30s 스로틀링)
    @Modifying
    @Query("UPDATE Commander c SET c.lastOnlineAt = :now WHERE c.id = :id AND (c.lastOnlineAt IS NULL OR c.lastOnlineAt < :threshold)")
    int updateLastOnlineAtIfStale(@Param("id") Long id, @Param("now") Instant now, @Param("threshold") Instant threshold);

    // 플레이타임 세션 근사 계산용 — 하트비트 갱신 직전의 lastOnlineAt 조회
    @Query("SELECT c.lastOnlineAt FROM Commander c WHERE c.id = :id")
    Instant findLastOnlineAtById(@Param("id") Long id);

    // 동시접속자 스냅샷 — 최근 threshold 이내 하트비트가 있었던 커맨더 수
    long countByLastOnlineAtAfter(Instant threshold);

    // 어드민 유저 검색
    Page<Commander> findByCommanderNameContainingIgnoreCase(String commanderName, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Commander c WHERE c.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") Long accountId);
}
