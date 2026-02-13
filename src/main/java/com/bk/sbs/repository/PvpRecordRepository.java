package com.bk.sbs.repository;

import com.bk.sbs.entity.PvpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PvpRecordRepository extends JpaRepository<PvpRecord, Long> {
    Optional<PvpRecord> findByCharacterId(Long characterId);
}
