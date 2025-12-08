package com.bk.sbs.config;

public class GameSettings {
    private String version = "1.0.0";
    private int maxLives = 3;

    // Fleet Settings
    private int maxShipsPerFleet = 10;
    private int shipAddMoneyCost = 1000;
    private int shipAddMineralCost = 500;

    // Enemy Settings
    private float enemyFleetSpawnInterval = 5.0f;
    private float explorationInterval = 15.0f;
    private float enemySpawnRate = 2.0f;

    // Getters and Setters
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getMaxLives() {
        return maxLives;
    }

    public void setMaxLives(int maxLives) {
        this.maxLives = maxLives;
    }

    public int getMaxShipsPerFleet() {
        return maxShipsPerFleet;
    }

    public void setMaxShipsPerFleet(int maxShipsPerFleet) {
        this.maxShipsPerFleet = maxShipsPerFleet;
    }

    public int getShipAddMoneyCost() {
        return shipAddMoneyCost;
    }

    public void setShipAddMoneyCost(int shipAddMoneyCost) {
        this.shipAddMoneyCost = shipAddMoneyCost;
    }

    public int getShipAddMineralCost() {
        return shipAddMineralCost;
    }

    public void setShipAddMineralCost(int shipAddMineralCost) {
        this.shipAddMineralCost = shipAddMineralCost;
    }

    public float getEnemyFleetSpawnInterval() {
        return enemyFleetSpawnInterval;
    }

    public void setEnemyFleetSpawnInterval(float enemyFleetSpawnInterval) {
        this.enemyFleetSpawnInterval = enemyFleetSpawnInterval;
    }

    public float getExplorationInterval() {
        return explorationInterval;
    }

    public void setExplorationInterval(float explorationInterval) {
        this.explorationInterval = explorationInterval;
    }

    public float getEnemySpawnRate() {
        return enemySpawnRate;
    }

    public void setEnemySpawnRate(float enemySpawnRate) {
        this.enemySpawnRate = enemySpawnRate;
    }
}