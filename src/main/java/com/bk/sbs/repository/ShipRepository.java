package com.bk.sbs.repository;

import com.bk.sbs.entity.Ship;
import com.bk.sbs.entity.ShipModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipRepository extends JpaRepository<Ship, Long> {
    
    List<Ship> findByFleetIdAndDeletedFalseOrderByPositionIndex(Long fleetId);
    
    Optional<Ship> findByIdAndDeletedFalse(Long id);

    @Query("SELECT s FROM Ship s WHERE s.fleet.id = :fleetId AND s.fleet.characterId = :characterId AND s.deleted = false ORDER BY s.positionIndex")
    List<Ship> findByFleetIdAndCharacterId(@Param("fleetId") Long fleetId, @Param("characterId") Long characterId);
    
    boolean existsByFleetIdAndPositionIndexAndDeletedFalse(Long fleetId, int positionIndex);
}
