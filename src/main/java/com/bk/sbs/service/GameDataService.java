package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.config.DataTableDailyBonus;
import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.config.DataTablePvpSeason;
import com.bk.sbs.config.ZoneConfig;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.ModuleSlotInfoDto;
import com.bk.sbs.dto.GeneralSettings;
import com.bk.sbs.dto.ShipStatFormulaSettings;
import com.bk.sbs.dto.TacticSettings;
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
    // 커맨더 레벨 전체 데이터 (요구 exp, commandPower) — 함선 수는 더 이상 레벨로 제한하지 않음(지휘력만으로 배치 가능 여부 결정)
    private static class CommanderData {
        int requireExp;
        int commandPowerMax;
        CommanderData(int requireExp, int commandPowerMax) {
            this.requireExp = requireExp;
            this.commandPowerMax = commandPowerMax;
        }
    }
    private Map<Integer, CommanderData> commanderDataMap = new HashMap<>();

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

    // 업적 정의 1개 — 클라 AchievementData(CSV 정적 정의)와 1:1. 완료/수령 여부는 여기 없고 AchievementService가 런타임에 계산
    public static class AchievementEntry {
        public String achievementId;
        public com.bk.sbs.enums.EAchievementConditionType conditionType;
        public String conditionParam;
        public int threshold;
        public int achievementPointReward;
        public int achievementPointRewardVip;
        public AchievementEntry(String achievementId, com.bk.sbs.enums.EAchievementConditionType conditionType,
                                 String conditionParam, int threshold, int achievementPointReward, int achievementPointRewardVip) {
            this.achievementId = achievementId;
            this.conditionType = conditionType;
            this.conditionParam = conditionParam;
            this.threshold = threshold;
            this.achievementPointReward = achievementPointReward;
            this.achievementPointRewardVip = achievementPointRewardVip;
        }
    }

    private java.util.List<AchievementEntry> achievementList = new java.util.ArrayList<>();

    // 일일 업적 정의 1개 — 클라 DailyAchievementData(CSV 정적 정의)와 1:1. 완료/수령 여부는 DailyAchievementService가 매 요청 "오늘" 기준으로 계산
    public static class DailyAchievementEntry {
        public String achievementId;
        public com.bk.sbs.enums.EAchievementConditionType conditionType;
        public String conditionParam;
        public int threshold;
        public int achievementPointReward;
        public int achievementPointRewardVip;
        public DailyAchievementEntry(String achievementId, com.bk.sbs.enums.EAchievementConditionType conditionType,
                                      String conditionParam, int threshold, int achievementPointReward, int achievementPointRewardVip) {
            this.achievementId = achievementId;
            this.conditionType = conditionType;
            this.conditionParam = conditionParam;
            this.threshold = threshold;
            this.achievementPointReward = achievementPointReward;
            this.achievementPointRewardVip = achievementPointRewardVip;
        }
    }

    private java.util.List<DailyAchievementEntry> dailyAchievementList = new java.util.ArrayList<>();

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
                        int commandPowerMax = levelNode.path("commandPower").asInt(0);
                        commanderDataMap.put(commanderLevel, new CommanderData(requireExp, commandPowerMax));
                    }
                }
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

            ClassPathResource achievementResource = new ClassPathResource("data/DataTableAchievement.json");
            if (achievementResource.exists()) {
                String json = new String(achievementResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                achievementList.clear();
                for (com.fasterxml.jackson.databind.JsonNode node : arrayNode) {
                    String achievementId = node.path("achievementId").asText(null);
                    if (achievementId == null) continue;
                    com.bk.sbs.enums.EAchievementConditionType conditionType =
                            com.bk.sbs.enums.EAchievementConditionType.valueOf(node.path("conditionType").asText());
                    achievementList.add(new AchievementEntry(
                            achievementId,
                            conditionType,
                            node.path("conditionParam").asText(""),
                            node.path("threshold").asInt(0),
                            node.path("achievementPointReward").asInt(0),
                            node.path("achievementPointRewardVip").asInt(0)));
                }
                log.info("DataTableAchievement.json loaded: {} entries", achievementList.size());
            } else {
                log.warn("DataTableAchievement.json not found in resources/data/, using empty data");
            }

            ClassPathResource dailyAchievementResource = new ClassPathResource("data/DataTableDailyAchievement.json");
            if (dailyAchievementResource.exists()) {
                String json = new String(dailyAchievementResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                dailyAchievementList.clear();
                for (com.fasterxml.jackson.databind.JsonNode node : arrayNode) {
                    String achievementId = node.path("achievementId").asText(null);
                    if (achievementId == null) continue;
                    com.bk.sbs.enums.EAchievementConditionType conditionType =
                            com.bk.sbs.enums.EAchievementConditionType.valueOf(node.path("conditionType").asText());
                    dailyAchievementList.add(new DailyAchievementEntry(
                            achievementId,
                            conditionType,
                            node.path("conditionParam").asText(""),
                            node.path("threshold").asInt(0),
                            node.path("achievementPointReward").asInt(0),
                            node.path("achievementPointRewardVip").asInt(0)));
                }
                log.info("DataTableDailyAchievement.json loaded: {} entries", dailyAchievementList.size());
            } else {
                log.warn("DataTableDailyAchievement.json not found in resources/data/, using empty data");
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

    // 함대 슬롯 칸 수 고정 상한 — 지휘관 레벨과 무관, 지휘력(CP)만으로 실제 배치 가능 여부가 결정됨(GeneralSettings.maxFleetSlotCount)
    public int getMaxShipsPerFleet() {
        GeneralSettings general = getDataTableConfig().getGeneral();
        Integer val = general != null ? general.getMaxFleetSlotCount() : null;
        return val != null ? val : 9;
    }

    public int getBattleRepairExplorationPointPerSec() {
        TacticSettings tactic = getDataTableConfig().getTactic();
        Integer val = tactic != null ? tactic.getTacticRepairCost() : null;
        return val != null ? val : 1;
    }

    // 광물 1개당 환산되는 자원 포인트 — 자원 충전(ExplorationService.chargeResource)에서 사용
    public int getMineralToResourceRatio() {
        TacticSettings tactic = getDataTableConfig().getTactic();
        Integer val = tactic != null ? tactic.getMineralToResourceRatio() : null;
        return val != null ? val : 1;
    }

    public List<ModuleData> getModulesByType(EModuleType moduleType) {
        return switch (moduleType) {
            case hull -> dataTableModule.getHullModules();
            case beam -> dataTableModule.getBeamModules();
            case missile -> dataTableModule.getMissileModules();
            case hangar -> dataTableModule.getHangarModules();
            case shield -> dataTableModule.getShieldModules();
            case interceptor -> dataTableModule.getInterceptorModules();
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


    // 지휘력(캐파) 최대치 — 구매 없이 커맨더 레벨만으로 결정(M13)
    public int getCommandPowerMax(int commanderLevel) {
        CommanderData data = commanderDataMap.get(commanderLevel);
        return data != null ? data.commandPowerMax : 0;
    }

    public ZoneConfig getZoneConfig() {
        return zoneConfig != null ? zoneConfig : new ZoneConfig();
    }

    // 해금 커맨더 레벨 이하인 hull(함체) 목록만 — 플레이어가 선택 가능한 함체 목록용
    // hullSubType(예: "hull_3_1_11100") → hull ModuleData 조회. 존재하지 않는 이름이면 null
    public ModuleData getHullModuleData(String hullSubType) {
        if (hullSubType == null) return null;
        return getModulesByType(EModuleType.hull).stream()
                .filter(d -> hullSubType.equals(d.getModuleSubType()))
                .findFirst().orElse(null);
    }

    public java.util.List<RewardCardEntry> getRewardCardList() {
        return rewardCardList;
    }

    public RewardCardEntry getRewardCard(String cardId) {
        return rewardCardById.get(cardId);
    }

    // hullSubType → [beam, missile, hangar, shield, interceptor] 카테고리별 최대 슬롯 수.
    // 아키타입 전용 프리팹에서 파싱된 ModuleData.moduleSlots를 그대로 집계 — 클라 CommonUtility.ParseHullSlotComposition과 동일한 소스/방식
    // (구 "hull_{tier}_{gen}_{5자리슬롯코드}" 네이밍 전용이던 문자열 파싱 방식은 새 아키타입 네이밍(hull_{tier}_{archetype}_{level})엔 맞지 않아 폐기)
    public int[] getMaxSlotsForHull(String hullSubType) {
        int[] result = new int[5];
        ModuleData hullData = getHullModuleData(hullSubType);
        if (hullData == null) return result;

        if (hullData.getModuleSlots() != null) {
            for (ModuleSlotInfoDto slot : hullData.getModuleSlots()) {
                int index = getSlotCompositionIndex(slot.getModuleType());
                if (index >= 0) result[index]++;
            }
        }

        // 실드/요격체는 3D ModuleSlot 마커가 없어 moduleSlots로는 절대 안 잡힘 — CSV의 has_shield/has_interceptor 플래그로 직접 채움
        result[3] = Boolean.TRUE.equals(hullData.getHasShield()) ? 1 : 0;
        result[4] = Boolean.TRUE.equals(hullData.getHasInterceptor()) ? 1 : 0;

        return result;
    }

    // getMaxSlotsForHull/getMaxSlotCountForArchetype 공용 — [beam, missile, hangar, shield, interceptor] 배열 인덱스 매핑
    private int getSlotCompositionIndex(EModuleType moduleType) {
        return switch (moduleType) {
            case beam -> 0;
            case missile -> 1;
            case hangar -> 2;
            case shield -> 3;
            case interceptor -> 4;
            default -> -1;
        };
    }

    // hullSubType(예: "hull_3_1_11100") → tier 정수(두 번째 토큰). 형식이 다르면 0
    public static int parseTierFromHullSubType(String hullSubType) {
        if (hullSubType == null) return 0;
        String[] parts = hullSubType.split("_");
        if (parts.length < 2) return 0;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // hullSubType(예: "hull_3_1_11100") → gen 정수(세 번째 토큰, 세대/외형 구분자). 형식이 다르면 0
    public static int parseGenFromHullSubType(String hullSubType) {
        if (hullSubType == null) return 0;
        String[] parts = hullSubType.split("_");
        if (parts.length < 3) return 0;
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    // 아키타입 단위 영구 모듈강화(M6) — 레벨업 비용을 datatable_module.csv의 레벨 행(ap_cost 컬럼, unlockAchievementPointCost 필드)에서 직접 조회
    // targetLevel: 이번 구매로 도달하는 효과 레벨(ArchetypeModuleUpgradeService가 구매횟수+2로 계산) — 그 레벨 행에 기록된 비용을 그대로 사용, 행이 없으면 null(레벨업 불가)
    public Integer getArchetypeLevelUpCost(String hullArchetype, EModuleType moduleType, int targetLevel) {
        String levelKey = buildArchetypeLevelKey(hullArchetype, moduleType, targetLevel);
        if (levelKey == null) return null;

        for (ModuleData data : getModulesByType(moduleType)) {
            if (levelKey.equals(data.getModuleSubType()))
                return data.getUnlockAchievementPointCost();
        }
        return null;
    }

    // hull: 해당 아키타입 행에서 tier 토큰을 그대로 가져와 "hull_{tier}_{archetype}_{level}" 조립
    // hull 외: 아키타입 무관 공용 티어행 "{moduleType}_{level}_1"
    private String buildArchetypeLevelKey(String hullArchetype, EModuleType moduleType, int level) {
        if (moduleType == EModuleType.hull) {
            for (ModuleData data : getModulesByType(EModuleType.hull)) {
                if (hullArchetype.equals(data.getHullArchetype()) == false) continue;
                int tier = parseTierFromHullSubType(data.getModuleSubType());
                return "hull_" + tier + "_" + hullArchetype + "_" + level;
            }
            return null;
        }
        return moduleType.name() + "_" + level + "_1";
    }

    // 아키타입 단위 영구 모듈강화(M6) — beam/missile/hangar는 슬롯별로 레벨이 독립이라, 그 아키타입 함체 프리팹이 실제로 가진 슬롯 개수로 slotIndex 범위를 검증
    // 아키타입당 전용 프리팹 1개라 레벨 무관하게 아무 레벨 행이나 moduleSlots는 동일 — 가장 먼저 찾은 행을 그대로 사용
    public int getMaxSlotCountForArchetype(String hullArchetype, EModuleType moduleType) {
        for (ModuleData data : getModulesByType(EModuleType.hull)) {
            if (hullArchetype.equals(data.getHullArchetype()) == false) continue;
            if (data.getModuleSlots() == null) return 0;

            int slotCount = 0;
            for (ModuleSlotInfoDto slot : data.getModuleSlots()) {
                if (slot.getModuleType() == moduleType) slotCount++;
            }
            return slotCount;
        }
        return 0;
    }

    public DataTablePvpSeason getDataTablePvpSeason() {
        return dataTablePvpSeason != null ? dataTablePvpSeason : new DataTablePvpSeason();
    }

    public DataTableDailyBonus getDataTableDailyBonus() {
        return dataTableDailyBonus != null ? dataTableDailyBonus : new DataTableDailyBonus();
    }

    public ZoneConfigData getZoneConfigByIndex(int zoneIndex) {
        return getZoneConfig().getZoneByIndex(zoneIndex);
    }

    public List<AchievementEntry> getAchievementList() {
        return achievementList;
    }

    public List<DailyAchievementEntry> getDailyAchievementList() {
        return dailyAchievementList;
    }

}
