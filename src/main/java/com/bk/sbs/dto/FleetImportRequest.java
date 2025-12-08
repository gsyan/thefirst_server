package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import java.util.List;

@Data
public class FleetImportRequest {
    private String fleetName;
    private String description;
    private boolean isActive;
    private List<ShipImportData> ships;
    
    @Data
    public static class ShipImportData {
        private String shipName;
        private int positionIndex;
        private String description;
        private List<ShipModuleImportData> modules;
    }
    
    @Data
    public static class ShipModuleImportData {
        private EModuleType moduleType;
        private int moduleLevel;
        private int slotIndex;
        
        // 모듈 스탯 정보 (클라이언트에서 계산된 값들)
        private float health;
        private int attackFireCount;
        private float attackPower;
        private float attackCoolTime;
        private float movementSpeed;
        private float rotationSpeed;
        private float cargoCapacity;
        private int upgradeMoneyCost;
        private int upgradeMineralCost;
    }
}
