package com.bk.sbs.repository;

import com.bk.sbs.entity.PvpSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PvpSeasonRepository extends JpaRepository<PvpSeason, Integer> {

    Optional<PvpSeason> findTopByOrderBySeasonNumberDesc();

    // 특정 시즌 번호의 보상 만료일(pvpPointExpiry)을 일괄 업데이트
    // pvpPointSeasonRef == seasonNumber 인 캐릭터 전체 적용
    @Modifying
    @Query("UPDATE Commander c SET c.pvpPointExpiry = :newExpiry WHERE c.pvpPointSeasonRef = :seasonNumber")
    int bulkUpdatePvpPointExpiry(@Param("seasonNumber") int seasonNumber, @Param("newExpiry") Instant newExpiry);
}

