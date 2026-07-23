package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.config.DataTableDailyBonus;
import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.config.DataTablePvpSeason;
import com.bk.sbs.config.ZoneConfig;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.enums.EDailyBonusTier;
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
    private DataTableDailyBonus dataTableDailyBonus = new DataTableDailyBonus();
    private ZoneConfig zoneConfig = new ZoneConfig();
    // 커맨더 레벨 전체 데이터 (요구 exp, shipCount)
    private static class CommanderData {
        int requireExp;
        int shipCount;
        CommanderData(int requireExp, int shipCount) {
            this.requireExp = requireExp;
            this.shipCount = shipCount;
        }
    }
    private Map<Integer, CommanderData> commanderDataMap = new HashMap<>();
    private int cachedMaxShipCount = 1;

    // ZoneEnemyFleetGenerator에서 클라(ExplorationEnemyFleetGenerator)와 동일한 함대를 재계산하는 데 필요한 최소 정보만 보관
    public static class ShipPresetSummary {
        public String presetId;
        public int unlockCommanderLevel;
        public int commandCost;
        public ShipPresetSummary(String presetId, int unlockCommanderLevel, int commandCost) {
            this.presetId = presetId;
            this.unlockCommanderLevel = unlockCommanderLevel;
            this.commandCost = commandCost;
        }
    }
    private java.util.List<ShipPresetSummary> shipPresetList = new java.util.ArrayList<>();
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

            ClassPathResource commanderResource = new ClassPathResource("data/DataTableCommander.json");
            if (commanderResource.exists()) {
                String json = new String(commanderResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                commanderDataMap.clear();
                for (com.fasterxml.jackson.databind.JsonNode levelNode : arrayNode) {
                    com.fasterxml.jackson.databind.JsonNode commanderLevelNode = levelNode.path("commanderLevel");
                    com.fasterxml.jackson.databind.JsonNode expNode = levelNode.path("requireExp");
                    if (commanderLevelNode.isMissingNode() == false && expNode.isMissingNode() == false) {
                        int commanderLevel = commanderLevelNode.asInt();
                        int requireExp     = expNode.asInt(0);
                        int shipCount       = levelNode.path("shipCount").asInt(1);
                        commanderDataMap.put(commanderLevel, new CommanderData(requireExp, shipCount));
                    }
                }
                cachedMaxShipCount = commanderDataMap.values().stream().mapToInt(d -> d.shipCount).max().orElse(1);
                log.info("DataTableCommander.json loaded: {} entries", commanderDataMap.size());
            } else {
                log.warn("DataTableCommander.json not found in resources/data/, using empty data");
            }

            ClassPathResource zoneConfigResource = new ClassPathResource("data/DataTableZone.json");
            if (zoneConfigResource.exists()) {
                String json = new String(zoneConfigResource.getInputStream().readAllBytes());
                zoneConfig = objectMapper.readValue(json, ZoneConfig.class);
                log.info("ZoneConfig.json loaded successfully from resources/data/");
            } else {
                log.warn("ZoneConfig.json not found in resources/data/, using empty data");
            }

            // ZoneEnemyFleetGenerator가 클라와 동일하게 셀 적함대를 재계산하는 데 필요 — presetId/unlockCommanderLevel/commandCost만 추출
            ClassPathResource shipPresetResource = new ClassPathResource("data/DataTableShipPreset.json");
            if (shipPresetResource.exists()) {
                String json = new String(shipPresetResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                shipPresetList.clear();
                for (com.fasterxml.jackson.databind.JsonNode presetNode : arrayNode) {
                    String presetId = presetNode.path("presetId").asText(null);
                    int unlockCommanderLevel = presetNode.path("unlockCommanderLevel").asInt(1);
                    int commandCost = presetNode.path("commandCost").asInt(0);
                    if (presetId != null)
                        shipPresetList.add(new ShipPresetSummary(presetId, unlockCommanderLevel, commandCost));
                }
                log.info("DataTableShipPreset.json loaded: {} entries", shipPresetList.size());
            } else {
                log.warn("DataTableShipPreset.json not found in resources/data/, using empty data");
            }

            ClassPathResource pvpSeasonResource = new ClassPathResource("data/DataTablePvpSeason.json");
            if (pvpSeasonResource.exists()) {
                String json = new String(pvpSeasonResource.getInputStream().readAllBytes());
                dataTablePvpSeason = objectMapper.readValue(json, DataTablePvpSeason.class);
                log.info("DataTablePvpSeason.json loaded successfully from resources/data/");
            } else {
                log.warn("DataTablePvpSeason.json not found in resources/data/, using default tier data");
            }

            ClassPathResource dailyBonusResource = new ClassPathResource("data/DataTableDailyBonus.json");
            if (dailyBonusResource.exists()) {
                String json = new String(dailyBonusResource.getInputStream().readAllBytes());
                java.util.List<DataTableDailyBonus.DayConfig> days = objectMapper.readValue(json,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, DataTableDailyBonus.DayConfig.class));
                dataTableDailyBonus = new DataTableDailyBonus(days);
                log.info("DataTableDailyBonus.json loaded successfully: {}일치", days.size());
            } else {
                log.warn("DataTableDailyBonus.json not found in resources/data/, using fallback properties value");
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

    public int getBattleRepairMineralPerSec() {
        Integer val = getDataTableConfig().getRepairBoostMineralPerSec();
        return val != null ? val : 1;
    }

    public int getInstantRepairBaseSecs() {
        Integer val = getDataTableConfig().getInstantRepairBaseSecs();
        return val != null ? val : 60;
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

    // 레벨업 기준 누적 exp 반환 (차감 없음, 서버 자동 판정 기준)
    public int getCommanderLevelRequiredExp(int commanderLevel) {
        CommanderData data = commanderDataMap.get(commanderLevel);
        return data != null ? data.requireExp : 0;
    }

    // 해당 커맨더 레벨에서 허용되는 최대 함선 수 반환
    public int getShipCount(int commanderLevel) {
        CommanderData data = commanderDataMap.get(commanderLevel);
        return data != null ? data.shipCount : 1;
    }

    public ZoneConfig getZoneConfig() {
        return zoneConfig != null ? zoneConfig : new ZoneConfig();
    }

    public java.util.List<ShipPresetSummary> getShipPresetList() {
        return shipPresetList;
    }

    public DataTablePvpSeason getDataTablePvpSeason() {
        return dataTablePvpSeason != null ? dataTablePvpSeason : new DataTablePvpSeason();
    }

    // tier("Normal"/"VIP") 기준 day의 Mineral 보상 반환. 테이블에 없으면 -1
    public int getDailyMineralForDay(int day, EDailyBonusTier tier) {
        return dataTableDailyBonus.getMineralForDay(day, tier);
    }

    // fromDay~toDay 구간 VIP Mineral 합산 (catch-up용)
    public int getVipMineralCatchup(int fromDay, int toDay) {
        return dataTableDailyBonus.getVipMineralCatchup(fromDay, toDay);
    }

    public ZoneConfigData getZoneConfigByIndex(int zoneIndex) {
        return getZoneConfig().getZoneByIndex(zoneIndex);
    }

}
