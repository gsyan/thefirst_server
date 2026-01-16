package com.bk.sbs.repository;

import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.entity.ShipModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipModuleRepository extends JpaRepository<ShipModule, Long> {

    // ship_id
    List<ShipModule> findByShipIdAndDeletedFalseOrderBySlotIndex(Long shipId);

    // ship_id, body_index, module_type, module_sub_type, slot_index
    Optional<ShipModule> findByShipIdAndBodyIndexAndModuleTypeAndModuleSubTypeAndSlotIndexAndDeletedFalse(
            Long shipId, int bodyIndex, EModuleType moduleType, EModuleSubType moduleSubType, int slotIndex);

    // ship_id, body_index, module_type, slot_index
    Optional<ShipModule> findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
            Long shipId, int bodyIndex, EModuleType moduleType, int slotIndex);

    // ship_id, body_index (특정 바디의 모든 모듈 조회)
    List<ShipModule> findByShipIdAndBodyIndexAndDeletedFalse(Long shipId, int bodyIndex);
}
