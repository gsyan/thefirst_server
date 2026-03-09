package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.config.ZoneConfig;
import com.bk.sbs.dto.ZoneConfigData;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GameDataService {
    private DataTableConfig dataTableConfig;
    private DataTableModule dataTableModule = new DataTableModule();
    private ZoneConfig zoneConfig = new ZoneConfig();
    // researchId → 연구 비용 (tech_level_N 등)
    private Map<String, CostStructDto> techLevelResearchCostMap = new HashMap<>();

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
                // techLevelDataList: researchId → researchCost 맵으로 로드
                com.fasterxml.jackson.databind.JsonNode techLevelDataListNode = rootNode.get("techLevelDataList");
                if (techLevelDataListNode != null) {
                    techLevelResearchCostMap.clear();
                    for (com.fasterxml.jackson.databind.JsonNode techNode : techLevelDataListNode) {
                        String rId = techNode.path("researchId").asText(null);
                        com.fasterxml.jackson.databind.JsonNode costNode = techNode.path("researchCost");
                        if (rId != null && !costNode.isMissingNode()) {
                            CostStructDto cost = objectMapper.convertValue(costNode, CostStructDto.class);
                            techLevelResearchCostMap.put(rId, cost);
                        }
                    }
                    log.info("techLevelDataList loaded: {} entries", techLevelResearchCostMap.size());
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
        return getDataTableConfig().getMaxShipsPerFleet();
    }

    public CostStructDto getShipAddCost(int currentShipCount) {
        List<CostStructDto> costs = getDataTableConfig().getAddShipCosts();
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
            case body -> dataTableModule.getBodyModules();
            case beam -> dataTableModule.getBeamModules();
            case missile -> dataTableModule.getMissileModules();
            case engine -> dataTableModule.getEngineModules();
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

    // 새 모듈 subType에 해당하는 교체(적용) 비용 반환 (없으면 기본값 MR 5000)
    public CostStructDto getModuleChangeCost(EModuleSubType newSubType) {
        List<com.bk.sbs.dto.ModuleChangeCostEntryDto> costs = getDataTableConfig().getModuleChangeCosts();
        if (costs == null || costs.isEmpty()) {
            return new CostStructDto(0, 0L, 5000L, 0L, 0L);
        }
        return costs.stream()
                .filter(e -> newSubType.equals(e.getModuleSubType()))
                .map(com.bk.sbs.dto.ModuleChangeCostEntryDto::getCost)
                .findFirst()
                .orElse(new CostStructDto(0, 0L, 5000L, 0L, 0L));
    }

    public CostStructDto getModuleResearchCost(EModuleSubType moduleSubType) {
        if (dataTableModule == null) {
            return new CostStructDto(0, 0L, 0L, 0L, 0L);
        }
        return dataTableModule.getResearchCost(moduleSubType);
    }

    // tech_level_N 연구 비용 반환 (데이터 없으면 비용 0)
    public CostStructDto getTechLevelResearchCost(String researchId) {
        CostStructDto cost = techLevelResearchCostMap.get(researchId);
        return cost != null ? cost : new CostStructDto(0, 0L, 0L, 0L, 0L);
    }

    public ZoneConfig getZoneConfig() {
        return zoneConfig != null ? zoneConfig : new ZoneConfig();
    }

    public ZoneConfigData getZoneConfigByName(String zoneName) {
        return getZoneConfig().getZoneByName(zoneName);
    }

    public List<ZoneConfigData> getAllZoneConfigsUpTo(String zoneName) {
        return getZoneConfig().getAllZonesUpTo(zoneName);
    }
}
