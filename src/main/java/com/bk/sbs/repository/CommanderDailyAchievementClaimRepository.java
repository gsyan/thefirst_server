package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderDailyAchievementClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CommanderDailyAchievementClaimRepository extends JpaRepository<CommanderDailyAchievementClaim, Long> {

    boolean existsByCommanderIdAndDailyAchievementIdAndClaimDate(Long commanderId, String dailyAchievementId, LocalDate claimDate);

    List<CommanderDailyAchievementClaim> findByCommanderIdAndClaimDate(Long commanderId, LocalDate claimDate);
}
