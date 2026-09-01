package com.bk.sbs.repository;

import com.bk.sbs.entity.Fleet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FleetRepository extends JpaRepository<Fleet, Long> {

    Optional<Fleet> findByCommanderIdAndFleetIndex(Long commanderId, int fleetIndex);

    List<Fleet> findByCommanderIdOrderByFleetIndex(Long commanderId);

    // id로 조회하되 commanderId까지 함께 검증 — 다른 커맨더 소유 함대를 id만으로 조작하지 못하도록 방지
    Optional<Fleet> findByIdAndCommanderId(Long id, Long commanderId);

    @Modifying
    @Query("DELETE FROM Fleet f WHERE f.commanderId = :commanderId")
    void deleteByCommanderId(@Param("commanderId") Long commanderId);
}
