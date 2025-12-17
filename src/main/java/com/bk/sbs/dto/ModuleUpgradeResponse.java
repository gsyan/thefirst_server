package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleUpgradeResponse {
    private boolean success;
    private int newLevel;
    private ModuleStats newStats;
    private CostRemainInfo costRemainInfo;
    private String message;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleStats {
        private float health;
        private float attackPower;
        private float movementSpeed;
        private float rotationSpeed;
        private float cargoCapacity;
    }
}