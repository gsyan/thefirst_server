package com.bk.sbs.repository;

import com.bk.sbs.entity.PvpSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PvpSeasonRepository extends JpaRepository<PvpSeason, Integer> {

    Optional<PvpSeason> findTopByOrderBySeasonNumberDesc();
}

