package com.bk.sbs.repository;

import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.entity.ShipModuleLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipModuleLevelRepository extends JpaRepository<ShipModuleLevel, Long> {

    // 특정 슬롯의 특정 서브타입 레벨 조회
    Optional<ShipModuleLevel> findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndModuleSubType(
            Long shipId, int bodyIndex, EModuleType moduleType, int slotIndex, EModuleSubType moduleSubType);
}
