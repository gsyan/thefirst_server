package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderAchievementClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommanderAchievementClaimRepository extends JpaRepository<CommanderAchievementClaim, Long> {

    boolean existsByCommanderIdAndAchievementIdAndIsVip(Long commanderId, String achievementId, boolean isVip);

    List<CommanderAchievementClaim> findByCommanderId(Long commanderId);
}
