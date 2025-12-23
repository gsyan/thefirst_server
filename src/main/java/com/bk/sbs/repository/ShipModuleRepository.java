package com.bk.sbs.repository;

import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.entity.ShipModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipModuleRepository extends JpaRepository<ShipModule, Long> {
    
    List<ShipModule> findByShipIdAndDeletedFalseOrderBySlotIndex(Long shipId);
    
    Optional<ShipModule> findByIdAndDeletedFalse(Long id);
    
    List<ShipModule> findByShipIdAndModuleTypeAndDeletedFalse(Long shipId, EModuleType moduleType);

    @Query("SELECT sm FROM ShipModule sm WHERE sm.ship.id = :shipId AND sm.ship.fleet.characterId = :characterId AND sm.deleted = false ORDER BY sm.slotIndex")
    List<ShipModule> findByShipIdAndCharacterId(@Param("shipId") Long shipId, @Param("characterId") Long characterId);
    
    boolean existsByShipIdAndSlotIndexAndDeletedFalse(Long shipId, int slotIndex);
    
    Optional<ShipModule> findByShipIdAndSlotIndexAndDeletedFalse(Long shipId, int slotIndex);

    Optional<ShipModule> findByShipIdAndBodyIndexAndModuleTypeAndDeletedFalse(Long shipId, int bodyIndex, EModuleType moduleType);

    Optional<ShipModule> findByShipIdAndBodyIndexAndSlotIndexAndDeletedFalse(Long shipId, int bodyIndex, int slotIndex);
}
