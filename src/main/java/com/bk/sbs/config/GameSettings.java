package com.bk.sbs.config;

import com.bk.sbs.dto.CostStruct;
import lombok.Data;

import java.util.List;

@Data
public class GameSettings {
    private String version = "1.0.0";
    private int maxLives = 3;

    // Fleet Settings
    private int maxShipsPerFleet = 10;
    private List<CostStruct> addShipCosts;

    // Formation Settings
    private float linearFormationSpacing = 3.0f;
    private float gridFormationSpacing = 5.0f;
    private float circleFormationSpacing = 8.0f;
    private float diamondFormationSpacing = 6.0f;
    private float wedgeFormationSpacing = 3.0f;

    // Enemy Settings
    private float enemyFleetSpawnInterval = 5.0f;
    private float explorationInterval = 15.0f;
    private float enemySpawnRate = 2.0f;
}