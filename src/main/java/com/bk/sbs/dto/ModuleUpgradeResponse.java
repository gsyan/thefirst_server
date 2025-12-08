package com.bk.sbs.dto;

public class ModuleUpgradeResponse {
    private boolean success;
    private int newLevel;
    private ModuleStats newStats;
    private CostInfo totalCost;
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public void setNewLevel(int newLevel) {
        this.newLevel = newLevel;
    }

    public ModuleStats getNewStats() {
        return newStats;
    }

    public void setNewStats(ModuleStats newStats) {
        this.newStats = newStats;
    }

    public CostInfo getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(CostInfo totalCost) {
        this.totalCost = totalCost;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class ModuleStats {
        private float health;
        private float attackPower;
        private float movementSpeed;
        private float rotationSpeed;
        private float cargoCapacity;

        public float getHealth() {
            return health;
        }

        public void setHealth(float health) {
            this.health = health;
        }

        public float getAttackPower() {
            return attackPower;
        }

        public void setAttackPower(float attackPower) {
            this.attackPower = attackPower;
        }

        public float getMovementSpeed() {
            return movementSpeed;
        }

        public void setMovementSpeed(float movementSpeed) {
            this.movementSpeed = movementSpeed;
        }

        public float getRotationSpeed() {
            return rotationSpeed;
        }

        public void setRotationSpeed(float rotationSpeed) {
            this.rotationSpeed = rotationSpeed;
        }

        public float getCargoCapacity() {
            return cargoCapacity;
        }

        public void setCargoCapacity(float cargoCapacity) {
            this.cargoCapacity = cargoCapacity;
        }
    }

}