package com.bk.sbs.service;

import com.bk.sbs.config.GameSettings;
import com.bk.sbs.config.ModuleDataTable;
import com.bk.sbs.dto.CostStructDto;
import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.ModuleResearchData;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
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

            ClassPathResource researchDataTableResource = new ClassPathResource("data/DataTableModuleResearch.json");
            if (researchDataTableResource.exists()) {
                String json = new String(researchDataTableResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(json);
                com.fasterxml.jackson.databind.JsonNode researchDataListNode = rootNode.get("researchDataList");
                if (researchDataListNode != null) {
                    List<ModuleResearchData> researchDataList = objectMapper.convertValue(
                        researchDataListNode,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ModuleResearchData.class)
                    );
                    moduleDataTable.setResearchDataList(researchDataList);
                    log.info("DataTableModuleResearch.json loaded successfully from resources/data/ and merged into ModuleDataTable");
                }
            } else {
                log.warn("DataTableModuleResearch.json not found in resources/data/, using empty data");
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

    public CostStructDto getShipAddCost(int currentShipCount) {
        List<CostStructDto> costs = getGameSettings().getAddShipCosts();
        if (costs == null || costs.isEmpty()) {
            return new CostStructDto(0, 0L, 0L, 0L, 0L);
        }

        // 현재 함선 수에 해당하는 비용 반환 (인덱스는 0부터 시작)
        if (currentShipCount >= 0 && currentShipCount < costs.size()) {
            return costs.get(currentShipCount);
        }

        // 범위를 벗어나면 마지막 비용 반환
        return costs.getLast();
    }

    public List<ModuleData> getModulesByType(EModuleType moduleType) {
        return switch (moduleType) {
            case Body -> moduleDataTable.getBodyModules();
            case Weapon -> moduleDataTable.getWeaponModules();
            case Engine -> moduleDataTable.getEngineModules();
            case Hanger -> moduleDataTable.getHangerModules();
            default -> throw new BusinessException(ServerErrorCode.UNKNOWN_ERROR);
        };
    }

    public ModuleData getFirstModuleByType(EModuleType moduleType) {
        List<ModuleData> modules = getModulesByType(moduleType);
        return modules.isEmpty() ? new ModuleData() : modules.get(0);
    }

    public CostStructDto getModuleResearchCost(EModuleSubType moduleSubType) {
        if (moduleDataTable == null) {
            return new CostStructDto(0, 0L, 0L, 0L, 0L);
        }
        return moduleDataTable.getResearchCost(moduleSubType);
    }
}
