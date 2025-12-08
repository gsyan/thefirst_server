package com.bk.sbs.repository;

import com.bk.sbs.entity.Fleet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FleetRepository extends JpaRepository<Fleet, Long> {
    
    List<Fleet> findByCharacterIdAndDeletedFalse(Long characterId);
    
    Optional<Fleet> findByIdAndCharacterIdAndDeletedFalse(Long id, Long characterId);
    
    Optional<Fleet> findByCharacterIdAndIsActiveTrueAndDeletedFalse(Long characterId);
    
    @Query("SELECT f FROM Fleet f WHERE f.characterId = :characterId AND f.deleted = false ORDER BY f.isActive DESC, f.modified DESC")
    List<Fleet> findByCharacterIdOrderByActiveAndModified(@Param("characterId") Long characterId);
    
    boolean existsByCharacterIdAndFleetNameAndDeletedFalse(Long characterId, String fleetName);
}
