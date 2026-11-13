//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderDailyCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommanderDailyCounterRepository extends JpaRepository<CommanderDailyCounter, Long> {

    List<CommanderDailyCounter> findByCommanderIdAndCounterDate(Long commanderId, LocalDate counterDate);

    Optional<CommanderDailyCounter> findByCommanderIdAndCounterDateAndCounterType(Long commanderId, LocalDate counterDate, String counterType);

    // 행이 없으면 생성, 있으면 counter_value에 더함 — 호출한 서비스의 트랜잭션에 함께 묶임
    @Modifying
    @Query(value = "INSERT INTO commander_daily_counter (commander_id, counter_date, counter_type, counter_value) " +
                    "VALUES (:commanderId, :counterDate, :counterType, :amount) " +
                    "ON DUPLICATE KEY UPDATE counter_value = counter_value + VALUES(counter_value)",
            nativeQuery = true)
    void upsertAdd(@Param("commanderId") Long commanderId, @Param("counterDate") LocalDate counterDate,
                   @Param("counterType") String counterType, @Param("amount") long amount);
}
