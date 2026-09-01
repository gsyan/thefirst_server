package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.config.DataTableDailyBonus;
import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.config.DataTablePvpSeason;
import com.bk.sbs.config.ZoneConfig;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.ShipStatFormulaSettings;
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

    // 셀 클리어 보상카드 1종 — 클라 RewardCardData의 서버 필요 필드만(ExportToServerJson 참고). 서버는 후보 추첨(weight)과 즉시효과 적용(effectType/value1/value2)에만 씀
    public static class RewardCardEntry {
        public String cardId;
        public String effectType;
        public boolean isPersistent;
        public float value1;
        public float value2;
        public int weight;
        public RewardCardEntry(String cardId, String effectType, boolean isPersistent, float value1, float value2, int weight) {
            this.cardId = cardId;
            this.effectType = effectType;
            this.isPersistent = isPersistent;
            this.value1 = value1;
            this.value2 = value2;
            this.weight = weight;
        }
    }

    private java.util.List<RewardCardEntry> rewardCardList = new java.util.ArrayList<>();
    private java.util.Map<String, RewardCardEntry> rewardCardById = new java.util.HashMap<>();
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

            ClassPathResource rewardCardResource = new ClassPathResource("data/DataTableRewardCard.json");
            if (rewardCardResource.exists()) {
                String json = new String(rewardCardResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                rewardCardList.clear();
                rewardCardById.clear();
                for (com.fasterxml.jackson.databind.JsonNode cardNode : arrayNode) {
                    String cardId = cardNode.path("cardId").asText(null);
                    if (cardId == null) continue;
                    RewardCardEntry entry = new RewardCardEntry(
                            cardId,
                            cardNode.path("effectType").asText(null),
                            cardNode.path("isPersistent").asBoolean(false),
                            (float) cardNode.path("value1").asDouble(0),
                            (float) cardNode.path("value2").asDouble(0),
                            cardNode.path("weight").asInt(1));
                    rewardCardList.add(entry);
                    rewardCardById.put(cardId, entry);
                }
                log.info("DataTableRewardCard.json loaded: {} entries", rewardCardList.size());
            } else {
                log.warn("DataTableRewardCard.json not found in resources/data/, using empty data");
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

    public int getInitialCommandPowerMax() {
        Integer val = getDataTableConfig().getCommandPowerMaxInit();
        return val != null ? val : 120;
    }

    public Integer getModuleUnlockPrice() {
        return getDataTableConfig().getModuleUnlockPrice();
    }

    public int getMaxAttackReinforcePointsPerSlot() {
        ShipStatFormulaSettings formula = getDataTableConfig().getShipStatFormula();
        if (formula == null) return 10;
        Integer maxPoints = formula.getMaxAttackReinforcePointsPerSlot();
        if (maxPoints == null) return 10;
        return maxPoints;
    }

    public int getBattleRepairExplorationPointPerSec() {
        Integer val = getDataTableConfig().getRepairBoostExplorationPointPerSec();
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
            case hangar -> dataTableModule.getHangarModules();
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

    // 해금 커맨더 레벨 이하인 body(함체) 목록만 — 플레이어가 선택 가능한 함체 목록용
    public java.util.List<ModuleData> getUnlockedBodyModules(int commanderLevel) {
        return getModulesByType(EModuleType.body).stream()
                .filter(d -> (d.getUnlockCommanderLevel() != null ? d.getUnlockCommanderLevel() : 1) <= commanderLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    // hullSubType(예: "h1_11100") → body ModuleData 조회. 존재하지 않는 이름이면 null
    public ModuleData getHullModuleData(String hullSubType) {
        if (hullSubType == null) return null;
        try {
            EModuleSubType subType = EModuleSubType.valueOf(hullSubType);
            return getModulesByType(EModuleType.body).stream()
                    .filter(d -> subType.equals(d.getModuleSubType()))
                    .findFirst().orElse(null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public java.util.List<RewardCardEntry> getRewardCardList() {
        return rewardCardList;
    }

    public RewardCardEntry getRewardCard(String cardId) {
        return rewardCardById.get(cardId);
    }

    // hullSubType(예: "h1_11100") → [beam, missile, hangar, shield, interceptor] 카테고리별 최대 슬롯 수.
    // 이름 규칙: "h" + tier(1자리) + "_" + 5자리 슬롯코드 — 형식이 다르면 전부 0(안전하게 막힘)
    // FleetService.setFleetSlotModules와 ZoneEnemyFleetGenerator(적 함대 모듈 다양성)가 공유
    public static int[] parseMaxSlotsFromHullSubType(String hullSubType) {
        int[] result = new int[5];
        if (hullSubType == null || hullSubType.length() != 8 || hullSubType.charAt(0) != 'h') return result;
        for (int i = 0; i < 5; i++) {
            char c = hullSubType.charAt(3 + i);
            if (Character.isDigit(c) == false) return new int[5];
            result[i] = Character.getNumericValue(c);
        }
        return result;
    }

    public int getModuleStatPoint(EModuleType moduleType, EModuleSubType subType) {
        List<ModuleData> modules = getModulesByType(moduleType);
        for (ModuleData data : modules) {
            if (subType.equals(data.getModuleSubType()))
                return data.getStatPoint() != null ? data.getStatPoint() : 0;
        }
        return 0;
    }

    public DataTablePvpSeason getDataTablePvpSeason() {
        return dataTablePvpSeason != null ? dataTablePvpSeason : new DataTablePvpSeason();
    }

    public ZoneConfigData getZoneConfigByIndex(int zoneIndex) {
        return getZoneConfig().getZoneByIndex(zoneIndex);
    }

}
