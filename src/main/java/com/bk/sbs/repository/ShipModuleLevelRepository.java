package com.bk.sbs.repository;

import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.entity.ShipModuleLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipModuleLevelRepository extends JpaRepository<ShipModuleLevel, Long> {

    // 특정 슬롯의 특정 서브타입 레벨 조회
    Optional<ShipModuleLevel> findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndModuleSubType(
            Long shipId, int bodyIndex, EModuleType moduleType, int slotIndex, EModuleSubType moduleSubType);

    // 특정 슬롯의 전체 서브타입 목록 조회 (unlockedSubTypes 응답용)
    List<ShipModuleLevel> findAllByShipIdAndBodyIndexAndModuleTypeAndSlotIndex(
            Long shipId, int bodyIndex, EModuleType moduleType, int slotIndex);

    // 함선 전체 레벨 이력 조회 (로드 시 unlockedSubTypes 일괄 빌드용)
    List<ShipModuleLevel> findAllByShipId(Long shipId);
}
