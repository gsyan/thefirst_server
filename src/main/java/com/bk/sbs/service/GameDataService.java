package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.config.DataTablePvpSeason;
import com.bk.sbs.config.ZoneConfig;
import com.bk.sbs.dto.ZoneConfigData;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GameDataService {
    private DataTableConfig dataTableConfig;
    private DataTableModule dataTableModule = new DataTableModule();
    private DataTablePvpSeason dataTablePvpSeason = new DataTablePvpSeason();
    private ZoneConfig zoneConfig = new ZoneConfig();
    // researchId → 기술레벨 전체 데이터 (비용, shipCount)
    private static class TechLevelData {
        int mineralCost;
        int shipCount;
        TechLevelData(int mineralCost, int shipCount) {
            this.mineralCost = mineralCost; this.shipCount = shipCount;
        }
    }
    private Map<String, TechLevelData> techLevelDataMap = new HashMap<>();
    private int cachedMaxShipCount = 1;
    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void loadInitialData() {
        try {
            ClassPathResource gameConfigResource = new ClassPathResource("data/DataTableConfig.json");
            if (gameConfigResource.exists()) {
                String json = new String(gameConfigResource.getInputStream().readAllBytes());
                dataTableConfig = objectMapper.readValue(json, DataTableConfig.class);
                log.info("GameConfig.json loaded successfully from resources/data/");
            } else {
                log.warn("No game config files found in resources/data/, using empty data");
            }

            ClassPathResource dataTableResource = new ClassPathResource("data/DataTableModule.json");
            if (dataTableResource.exists()) {
                String json = new String(dataTableResource.getInputStream().readAllBytes());
                dataTableModule = objectMapper.readValue(json, DataTableModule.class);
                log.info("DataTableModule.json loaded successfully from resources/data/ (fallback mode)");
            } else {
                log.warn("No game data files found in resources/data/, using empty data");
            }

            ClassPathResource researchDataTableResource = new ClassPathResource("data/DataTableResearch.json");
            if (researchDataTableResource.exists()) {
                String json = new String(researchDataTableResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(json);
                com.fasterxml.jackson.databind.JsonNode researchDataListNode = rootNode.get("researchDataList");
                if (researchDataListNode != null) {
                    List<ModuleResearchData> researchDataList = objectMapper.convertValue(
                        researchDataListNode,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ModuleResearchData.class)
                    );
                    dataTableModule.setResearchDataList(researchDataList);
                    log.info("DataTableResearch.json loaded successfully from resources/data/ and merged into ModuleDataTable");
                }
                // techLevelDataList: researchId → TechLevelData(cost, shipCount)
                com.fasterxml.jackson.databind.JsonNode techLevelDataListNode = rootNode.get("techLevelDataList");
                if (techLevelDataListNode != null) {
                    techLevelDataMap.clear();
                    for (com.fasterxml.jackson.databind.JsonNode techNode : techLevelDataListNode) {
                        String rId = techNode.path("researchId").asText(null);
                        com.fasterxml.jackson.databind.JsonNode costNode = techNode.path("pointCost");
                        if (rId != null && !costNode.isMissingNode()) {
                            int cost = techNode.path("pointCost").asInt(1);
                            int shipCount = techNode.path("shipCount").asInt(1);
                            techLevelDataMap.put(rId, new TechLevelData(cost, shipCount));
                        }
                    }
                    cachedMaxShipCount = techLevelDataMap.values().stream().mapToInt(d -> d.shipCount).max().orElse(1);
                    log.info("techLevelDataList loaded: {} entries", techLevelDataMap.size());
                }
            } else {
                log.warn("DataTableResearch.json not found in resources/data/, using empty data");
            }

            ClassPathResource zoneConfigResource = new ClassPathResource("data/DataTableZone.json");
            if (zoneConfigResource.exists()) {
                String json = new String(zoneConfigResource.getInputStream().readAllBytes());
                zoneConfig = objectMapper.readValue(json, ZoneConfig.class);
                log.info("ZoneConfig.json loaded successfully from resources/data/");
            } else {
                log.warn("ZoneConfig.json not found in resources/data/, using empty data");
            }

            ClassPathResource pvpSeasonResource = new ClassPathResource("data/DataTablePvpSeason.json");
            if (pvpSeasonResource.exists()) {
                String json = new String(pvpSeasonResource.getInputStream().readAllBytes());
                dataTablePvpSeason = objectMapper.readValue(json, DataTablePvpSeason.class);
                log.info("DataTablePvpSeason.json loaded successfully from resources/data/");
            } else {
                log.warn("DataTablePvpSeason.json not found in resources/data/, using default tier data");
            }

        } catch (Exception e) {
            log.error("Failed to load game data: " + e.getMessage(), e);
            loadDefaultDataTableConfig();
        }
    }

    public void loadGameData(DataTableModule dataTable) {
        if (dataTable == null) {
            throw new BusinessException(ServerErrorCode.INVALID_DATA_TABLE);
        }

        this.dataTableModule = dataTable;
    }


    private void loadDefaultDataTableConfig() {
        this.dataTableConfig = new DataTableConfig();
        // Default values are already set in GameSettings constructor
        log.info("Using default game settings");
    }

    public DataTableConfig getDataTableConfig() { return dataTableConfig != null ? dataTableConfig : new DataTableConfig(); }

    public int getMaxShipsPerFleet() {
        return cachedMaxShipCount;
    }

    public Integer getShipAddCost() {
        return getDataTableConfig().getAddShipCost();
    }

    public Integer getModuleUnlockPrice() {
        return getDataTableConfig().getModuleUnlockPrice();
    }

    public List<ModuleData> getModulesByType(EModuleType moduleType) {
        return switch (moduleType) {
            case body -> dataTableModule.getBodyModules();
            case beam -> dataTableModule.getBeamModules();
            case missile -> dataTableModule.getMissileModules();
            case hanger -> dataTableModule.getHangerModules();
            default -> throw new BusinessException(ServerErrorCode.UNKNOWN_ERROR);
        };
    }

    public ModuleData getFirstModuleByType(EModuleType moduleType) {
        List<ModuleData> modules = getModulesByType(moduleType);
        return modules.isEmpty() ? new ModuleData() : modules.get(0);
    }

    // 특정 subType의 최대 레벨 반환 — moduleLevel 최댓값 기준
    public int getMaxModuleLevel(EModuleType moduleType, EModuleSubType moduleSubType) {
        List<ModuleData> modules = getModulesByType(moduleType);
        return modules.stream()
                .filter(m -> moduleSubType.equals(m.getModuleSubType()))
                .mapToInt(ModuleData::getModuleLevel)
                .max()
                .orElse(0);
    }

    public int getModuleResearchCost(EModuleSubType moduleSubType) {
        if (dataTableModule == null) {
            return 0;
        }
        return dataTableModule.getResearchCost(moduleSubType);
    }

    // newSubType이 currentSubType의 직접 다음 단계인지 확인 (prerequisiteIds 기준)
    public boolean isDirectNextStep(EModuleSubType currentSubType, EModuleSubType newSubType) {
        List<com.bk.sbs.dto.ModuleResearchData> list = dataTableModule.getResearchDataList();
        String currentResearchId = null;
        String newPrerequisiteMatch = null;

        for (com.bk.sbs.dto.ModuleResearchData data : list) {
            if (currentSubType.equals(data.getModuleSubType())) {
                currentResearchId = data.getResearchId();
            }
        }
        if (currentResearchId == null) return false;

        for (com.bk.sbs.dto.ModuleResearchData data : list) {
            if (newSubType.equals(data.getModuleSubType())) {
                List<String> prereqs = data.getPrerequisiteIds();
                return prereqs != null && prereqs.contains(currentResearchId);
            }
        }
        return false;
    }

    // tech_level_N 연구 비용 반환 (데이터 없으면 비용 0)
    public int getTechLevelResearchCost(String researchId) {
        TechLevelData data = techLevelDataMap.get(researchId);
        return data != null ? data.mineralCost : 0;
    }

    // 해당 기술레벨에서 허용되는 최대 함선 수 반환
    public int getShipCount(int techLevel) {
        TechLevelData data = techLevelDataMap.get("tech_level_" + techLevel);
        return data != null ? data.shipCount : 1;
    }

    public ZoneConfig getZoneConfig() {
        return zoneConfig != null ? zoneConfig : new ZoneConfig();
    }

    public DataTablePvpSeason getDataTablePvpSeason() {
        return dataTablePvpSeason != null ? dataTablePvpSeason : new DataTablePvpSeason();
    }

    public ZoneConfigData getZoneConfigByName(String zoneName) {
        return getZoneConfig().getZoneByName(zoneName);
    }

}
