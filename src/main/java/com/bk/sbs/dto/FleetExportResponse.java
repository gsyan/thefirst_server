package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import java.util.List;

@Data
public class FleetExportResponse {
    private String fleetName;
    private String description;
    private boolean isActive;
    private List<ShipExportData> ships;
    
    @Data
    public static class ShipExportData {
        private String shipName;
        private int positionIndex;
        private String description;
        private List<ShipModuleExportData> modules;
    }
    
    @Data
    public static class ShipModuleExportData {
        private EModuleType moduleType;
        private int moduleLevel;
        private int slotIndex;
        
        // 모든 스탯 정보
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
