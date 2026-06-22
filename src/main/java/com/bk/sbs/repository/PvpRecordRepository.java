package com.bk.sbs.repository;

import com.bk.sbs.entity.PvpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PvpRecordRepository extends JpaRepository<PvpRecord, Long> {
    Optional<PvpRecord> findByCommanderId(Long commanderId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM PvpRecord p WHERE p.commanderId = :commanderId")
    void deleteByCommanderId(@org.springframework.data.repository.query.Param("commanderId") Long commanderId);
}


