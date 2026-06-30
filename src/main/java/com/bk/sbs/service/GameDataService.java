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
    // 커맨더 레벨 전체 데이터 (요구 exp, shipCount, 레벨업 보상 modulePoint, 모듈 서브타입 등급 상한)
    private static class CommanderLevelData {
        int requireExp;
        int modulePointReward;
        int shipCount;
        int subtypeLevel;
        CommanderLevelData(int requireExp, int modulePointReward, int shipCount, int subtypeLevel) {
            this.requireExp = requireExp; this.modulePointReward = modulePointReward;
            this.shipCount = shipCount; this.subtypeLevel = subtypeLevel;
        }
    }
    private Map<Integer, CommanderLevelData> commanderLevelDataMap = new HashMap<>();
    private Map<Integer, Long> upgradeCostByTier = new HashMap<>();
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

            ClassPathResource upgradeCostResource = new ClassPathResource("data/DataTableUpgradeCost.json");
            if (upgradeCostResource.exists()) {
                String json = new String(upgradeCostResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                upgradeCostByTier.clear();
                for (com.fasterxml.jackson.databind.JsonNode node : arrayNode) {
                    com.fasterxml.jackson.databind.JsonNode gradeNode = node.path("subtypeGrade");
                    if (gradeNode.isMissingNode() == false) {
                        int grade = gradeNode.asInt();
                        long cost = node.path("modulePointCost").asLong(0);
                        upgradeCostByTier.put(grade, cost);
                    }
                }
                log.info("DataTableUpgradeCost.json loaded: {} entries", upgradeCostByTier.size());
            } else {
                log.warn("DataTableUpgradeCost.json not found in resources/data/, using empty data");
            }

            ClassPathResource commanderLevelResource = new ClassPathResource("data/DataTableCommanderLevel.json");
            if (commanderLevelResource.exists()) {
                String json = new String(commanderLevelResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                commanderLevelDataMap.clear();
                for (com.fasterxml.jackson.databind.JsonNode levelNode : arrayNode) {
                    com.fasterxml.jackson.databind.JsonNode commanderLevelNode = levelNode.path("commanderLevel");
                    com.fasterxml.jackson.databind.JsonNode expNode = levelNode.path("requireExp");
                    if (commanderLevelNode.isMissingNode() == false && expNode.isMissingNode() == false) {
                        int commanderLevel    = commanderLevelNode.asInt();
                        int requireExp        = expNode.asInt(0);
                        int modulePointReward = levelNode.path("modulePointReward").asInt(0);
                        int shipCount         = levelNode.path("shipCount").asInt(1);
                        int subtypeLevel      = levelNode.path("subtypeLevel").asInt(1);
                        commanderLevelDataMap.put(commanderLevel, new CommanderLevelData(requireExp, modulePointReward, shipCount, subtypeLevel));
                    }
                }
                cachedMaxShipCount = commanderLevelDataMap.values().stream().mapToInt(d -> d.shipCount).max().orElse(1);
                log.info("DataTableCommanderLevel.json loaded: {} entries", commanderLevelDataMap.size());
            } else {
                log.warn("DataTableCommanderLevel.json not found in resources/data/, using empty data");
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

    // 특정 subType의 최대 레벨 반환 — moduleLevel 최댓값 기준
    public int getMaxModuleLevel(EModuleType moduleType, EModuleSubType moduleSubType) {
        List<ModuleData> modules = getModulesByType(moduleType);
        return modules.stream()
                .filter(m -> moduleSubType.equals(m.getModuleSubType()))
                .mapToInt(ModuleData::getModuleLevel)
                .max()
                .orElse(0);
    }

    // 서브타입 등급(tier)별 등급업 비용 — prerequisites 체인 없이 (value/100)%100로 산출한 tier 기준 조회
    public int getModuleResearchCost(EModuleSubType moduleSubType) {
        if (moduleSubType == null) return 0;
        int tier = (moduleSubType.getValue() / 100) % 100;
        Long cost = upgradeCostByTier.get(tier);
        return cost != null ? cost.intValue() : 0;
    }

    // 특정 body subtype의 level 1 moduleSlots 반환 (다운그레이드 시 사라지는 슬롯 판별용)
    public List<com.bk.sbs.dto.ModuleSlotInfoDto> getBodyModuleSlots(EModuleSubType bodySubType) {
        return getModulesByType(EModuleType.body).stream()
                .filter(m -> bodySubType.equals(m.getModuleSubType()) && m.getModuleLevel() == 1)
                .findFirst()
                .map(ModuleData::getModuleSlots)
                .orElse(java.util.Collections.emptyList());
    }

    // newSubType이 currentSubType의 직접 다음 단계인지 확인 — prerequisites 체인 없이 인코딩 산술(+100)로 판정
    public boolean isDirectNextStep(EModuleSubType currentSubType, EModuleSubType newSubType) {
        return newSubType == getNextSubType(currentSubType);
    }

    // currentSubType의 직접 다음 단계 반환 (없으면 none)
    public EModuleSubType getNextSubType(EModuleSubType currentSubType) {
        if (currentSubType == null) return EModuleSubType.none;
        EModuleSubType next = EModuleSubType.fromValue(currentSubType.getValue() + 100);
        return next;
    }

    // currentSubType의 직접 이전 단계 반환 (없으면 none)
    public EModuleSubType getPrevSubType(EModuleSubType currentSubType) {
        if (currentSubType == null) return EModuleSubType.none;
        return EModuleSubType.fromValue(currentSubType.getValue() - 100);
    }

    // 레벨업 기준 누적 exp 반환 (차감 없음, 서버 자동 판정 기준)
    public int getCommanderLevelRequiredExp(int commanderLevel) {
        CommanderLevelData data = commanderLevelDataMap.get(commanderLevel);
        return data != null ? data.requireExp : 0;
    }

    // 해당 커맨더 레벨에서 허용되는 최대 함선 수 반환
    public int getShipCount(int commanderLevel) {
        CommanderLevelData data = commanderLevelDataMap.get(commanderLevel);
        return data != null ? data.shipCount : 1;
    }

    // 해당 레벨 도달 시 지급되는 모듈포인트 보상
    public int getModulePointReward(int commanderLevel) {
        CommanderLevelData data = commanderLevelDataMap.get(commanderLevel);
        return data != null ? data.modulePointReward : 0;
    }

    // 해당 커맨더 레벨에서 허용되는 모듈 서브타입 등급 상한
    public int getSubtypeLevel(int commanderLevel) {
        CommanderLevelData data = commanderLevelDataMap.get(commanderLevel);
        return data != null ? data.subtypeLevel : 1;
    }

    public ZoneConfig getZoneConfig() {
        return zoneConfig != null ? zoneConfig : new ZoneConfig();
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

    public ZoneConfigData getZoneConfigByName(String zoneName) {
        return getZoneConfig().getZoneByName(zoneName);
    }

}
