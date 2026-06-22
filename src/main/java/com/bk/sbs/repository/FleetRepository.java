package com.bk.sbs.repository;

import com.bk.sbs.entity.Fleet;
import com.bk.sbs.enums.EModuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FleetRepository extends JpaRepository<Fleet, Long> {
    
    List<Fleet> findByCommanderIdAndDeletedFalse(Long commanderId);
    
    Optional<Fleet> findByIdAndCommanderIdAndDeletedFalse(Long id, Long commanderId);
    
    Optional<Fleet> findByCommanderIdAndIsActiveTrueAndDeletedFalse(Long commanderId);
    
    @Query("SELECT f FROM Fleet f WHERE f.commanderId = :commanderId AND f.deleted = false ORDER BY f.isActive DESC, f.modified DESC")
    List<Fleet> findByCommanderIdOrderByActiveAndModified(@Param("commanderId") Long commanderId);
    
    boolean existsByCommanderIdAndFleetNameAndDeletedFalse(Long commanderId, String fleetName);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Fleet f WHERE f.commanderId = :commanderId")
    void deleteByCommanderId(@org.springframework.data.repository.query.Param("commanderId") Long commanderId);
}



