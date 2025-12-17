package com.bk.sbs.service;

import com.bk.sbs.config.GameSettings;
import com.bk.sbs.config.ModuleDataTable;
import com.bk.sbs.dto.CostStruct;
import com.bk.sbs.dto.ModuleBodyDataDto;
import com.bk.sbs.dto.ModuleWeaponDataDto;
import com.bk.sbs.dto.ModuleEngineDataDto;
import com.bk.sbs.dto.ModuleHangerDataDto;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
@Slf4j
public class GameDataService {
    private GameSettings gameSettings;
    private ModuleDataTable moduleDataTable = new ModuleDataTable();

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void loadInitialData() {
        try {
            ClassPathResource gameConfigResource = new ClassPathResource("data/DataTableConfig.json");
            if (gameConfigResource.exists()) {
                String json = new String(gameConfigResource.getInputStream().readAllBytes());
                gameSettings = objectMapper.readValue(json, GameSettings.class);
                log.info("GameConfig.json loaded successfully from resources/data/");
            } else {
                log.warn("No game config files found in resources/data/, using empty data");
            }

            ClassPathResource dataTableResource = new ClassPathResource("data/DataTableModule.json");
            if (dataTableResource.exists()) {
                String json = new String(dataTableResource.getInputStream().readAllBytes());
                moduleDataTable = objectMapper.readValue(json, ModuleDataTable.class);
                log.info("DataTableModule.json loaded successfully from resources/data/ (fallback mode)");
            } else {
                log.warn("No game data files found in resources/data/, using empty data");
            }
            //loadGameConfig(gameConfig);


        } catch (Exception e) {
            log.error("Failed to load game data: " + e.getMessage(), e);
            loadDefaultGameSettings();
        }
    }

    public void loadGameData(ModuleDataTable dataTable) {
        if (dataTable == null) {
            throw new BusinessException(ServerErrorCode.INVALID_DATA_TABLE);
        }

        this.moduleDataTable = dataTable;
    }


    private void loadDefaultGameSettings() {
        this.gameSettings = new GameSettings();
        // Default values are already set in GameSettings constructor
        log.info("Using default game settings");
    }

    public GameSettings getGameSettings() {
        return gameSettings != null ? gameSettings : new GameSettings();
    }

    public int getMaxShipsPerFleet() {
        return getGameSettings().getMaxShipsPerFleet();
    }

    public CostStruct getShipAddCost(int currentShipCount) {
        List<CostStruct> costs = getGameSettings().getAddShipCosts();
        if (costs == null || costs.isEmpty()) {
            return new CostStruct(0, 0L, 0L, 0L, 0L);
        }

        // 현재 함선 수에 해당하는 비용 반환 (인덱스는 0부터 시작)
        if (currentShipCount >= 0 && currentShipCount < costs.size()) {
            return costs.get(currentShipCount);
        }

        // 범위를 벗어나면 마지막 비용 반환
        return costs.get(costs.size() - 1);
    }

    public List<ModuleBodyDataDto> getBodyModules() {
        return moduleDataTable.getBodyModules();
    }

    public List<ModuleWeaponDataDto> getWeaponModules() {
        return moduleDataTable.getWeaponModules();
    }

    public List<ModuleEngineDataDto> getEngineModules() {
        return moduleDataTable.getEngineModules();
    }

    public List<ModuleHangerDataDto> getHangerModules() {
        return moduleDataTable.getHangerModules();
    }

    public ModuleBodyDataDto getFirstBodyModule() {
        List<ModuleBodyDataDto> modules = getBodyModules();
        return modules.isEmpty() ? new ModuleBodyDataDto() : modules.get(0);
    }

    public ModuleWeaponDataDto getFirstWeaponModule() {
        List<ModuleWeaponDataDto> modules = getWeaponModules();
        return modules.isEmpty() ? new ModuleWeaponDataDto() : modules.get(0);
    }

    public ModuleEngineDataDto getFirstEngineModule() {
        List<ModuleEngineDataDto> modules = getEngineModules();
        return modules.isEmpty() ? new ModuleEngineDataDto() : modules.get(0);
    }

    public ModuleHangerDataDto getFirstHangerModule() {
        List<ModuleHangerDataDto> modules = getHangerModules();
        return modules.isEmpty() ? new ModuleHangerDataDto() : modules.get(0);
    }
}