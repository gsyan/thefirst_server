//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderDailyPlaytime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface CommanderDailyPlaytimeRepository extends JpaRepository<CommanderDailyPlaytime, Long> {

    List<CommanderDailyPlaytime> findByCommanderIdAndPlayDateBetweenOrderByPlayDateAsc(
            Long commanderId, LocalDate fromDate, LocalDate toDate);

    // 하트비트마다 호출 — 행이 없으면 생성, 있으면 played_seconds에 더함
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO commander_daily_playtime (commander_id, play_date, played_seconds, last_heartbeat_at) " +
                    "VALUES (:commanderId, :playDate, :seconds, :now) " +
                    "ON DUPLICATE KEY UPDATE played_seconds = played_seconds + VALUES(played_seconds), last_heartbeat_at = VALUES(last_heartbeat_at)",
            nativeQuery = true)
    void upsertAddSeconds(@Param("commanderId") Long commanderId, @Param("playDate") LocalDate playDate,
                           @Param("seconds") long seconds, @Param("now") Instant now);
}
