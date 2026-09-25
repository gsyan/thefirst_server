package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderAchievementReached;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommanderAchievementReachedRepository extends JpaRepository<CommanderAchievementReached, Long> {

    boolean existsByCommanderIdAndAchievementId(Long commanderId, String achievementId);

    List<CommanderAchievementReached> findByCommanderId(Long commanderId);
}
