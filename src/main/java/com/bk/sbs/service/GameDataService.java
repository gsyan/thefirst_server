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

    // ZoneEnemyFleetGenerator가 웨이브 예산을 맞추는 데 필요한 최소 정보 + FleetService의 슬롯 배치/모듈 토글(지휘력 재계산, 기본 로드아웃 시딩)에 사용
    // prefabName/defaultModules는 FleetService용. maxSlots/fullEquipCost는 ZoneEnemyFleetGenerator의 적 함대 모듈 다양성용
    public static class ShipPresetSummary {
        public String presetId;
        public int unlockCommanderLevel;
        public int commandCost; // 기본 로드아웃(빔1) 기준 — 지휘력 계산/플레이어 프리셋 목록용, 적 함대 예산 계산에는 fullEquipCost를 씀
        public String prefabName;
        public java.util.List<DefaultModuleEntry> defaultModules;
        public int[] maxSlots; // [beam, missile, hanger, shield, interceptor] — presetId에서 파싱
        public int fullEquipCost; // 모든 슬롯을 다 채웠을 때의 지휘력 코스트 — 적 함대는 이 값 기준으로 예산을 맞추고 실제로는 일부만 랜덤 장착(과소비 방지)
        public ShipPresetSummary(String presetId, int unlockCommanderLevel, int commandCost, String prefabName, java.util.List<DefaultModuleEntry> defaultModules, int[] maxSlots, int fullEquipCost) {
            this.presetId = presetId;
            this.unlockCommanderLevel = unlockCommanderLevel;
            this.commandCost = commandCost;
            this.prefabName = prefabName;
            this.defaultModules = defaultModules;
            this.maxSlots = maxSlots;
            this.fullEquipCost = fullEquipCost;
        }
    }

    // 프리셋 배치(placeFleetPresetShip) 시 CommanderFleetPresetSlotModule로 그대로 시딩되는 기본 장착 모듈 1건 — 지금은 beam slot0=beam_t1뿐
    public static class DefaultModuleEntry {
        public EModuleType moduleType;
        public int slotIndex;
        public EModuleSubType moduleSubType;
        public DefaultModuleEntry(EModuleType moduleType, int slotIndex, EModuleSubType moduleSubType) {
            this.moduleType = moduleType;
            this.slotIndex = slotIndex;
            this.moduleSubType = moduleSubType;
        }
    }

    private java.util.List<ShipPresetSummary> shipPresetList = new java.util.ArrayList<>();
    private java.util.Map<String, ShipPresetSummary> shipPresetById = new java.util.HashMap<>();
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

            // ZoneEnemyFleetGenerator가 클라와 동일하게 셀 적함대를 재계산하는 데 필요 + FleetService의 슬롯 배치/모듈 토글 지휘력 계산에도 사용
            ClassPathResource shipPresetResource = new ClassPathResource("data/DataTableShipPreset.json");
            if (shipPresetResource.exists()) {
                String json = new String(shipPresetResource.getInputStream().readAllBytes());
                com.fasterxml.jackson.databind.JsonNode arrayNode = objectMapper.readTree(json);
                shipPresetList.clear();
                shipPresetById.clear();
                for (com.fasterxml.jackson.databind.JsonNode presetNode : arrayNode) {
                    String presetId = presetNode.path("presetId").asText(null);
                    int unlockCommanderLevel = presetNode.path("unlockCommanderLevel").asInt(1);
                    int commandCost = presetNode.path("commandCost").asInt(0);
                    String prefabName = presetNode.path("prefabName").asText(null);
                    java.util.List<DefaultModuleEntry> defaultModules = parseDefaultModules(presetNode.path("statAllocation"));
                    if (presetId != null)
                    {
                        int[] maxSlots = parseMaxSlotsFromPresetId(presetId);
                        int fullEquipCost = computeFullEquipCost(prefabName, maxSlots);
                        ShipPresetSummary summary = new ShipPresetSummary(presetId, unlockCommanderLevel, commandCost, prefabName, defaultModules, maxSlots, fullEquipCost);
                        shipPresetList.add(summary);
                        shipPresetById.put(presetId, summary);
                    }
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

    public int getInitialCommandPowerMax() {
        Integer val = getDataTableConfig().getCommandPowerMaxInit();
        return val != null ? val : 120;
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

    public ShipPresetSummary getShipPresetSummary(String presetId) {
        return shipPresetById.get(presetId);
    }

    // presetId(예: "m11100") → [beam, missile, hanger, shield, interceptor] 카테고리별 최대 슬롯 수. "m" + 5자리 숫자 형식 — 형식이 다르면 전부 0(안전하게 막힘)
    // FleetService.toggleFleetPresetSlotModule과 ZoneEnemyFleetGenerator(적 함대 모듈 다양성)가 공유
    public static int[] parseMaxSlotsFromPresetId(String presetId) {
        int[] result = new int[5];
        if (presetId == null || presetId.length() != 6 || presetId.charAt(0) != 'm') return result;
        for (int i = 0; i < 5; i++) {
            char c = presetId.charAt(1 + i);
            if (Character.isDigit(c) == false) return new int[5];
            result[i] = Character.getNumericValue(c);
        }
        return result;
    }

    // 바디 설치비 + [beam, missile, hanger] 각 카테고리 최대 슬롯 수만큼 기본 서브타입(t1)을 전부 채웠을 때의 지휘력 코스트
    private int computeFullEquipCost(String prefabName, int[] maxSlots) {
        int bodyCost = 0;
        if (prefabName != null) {
            try {
                bodyCost = getModuleStatPoint(EModuleType.body, EModuleSubType.valueOf(prefabName));
            } catch (IllegalArgumentException ignored) {
                // prefabName이 EModuleSubType에 없는 경우 — bodyCost 0으로 취급
            }
        }

        int beamCost = getModuleStatPoint(EModuleType.beam, EModuleSubType.beam_t1);
        int missileCost = getModuleStatPoint(EModuleType.missile, EModuleSubType.missile_t1);
        int hangerCost = getModuleStatPoint(EModuleType.hanger, EModuleSubType.hanger_t1);

        return bodyCost + maxSlots[0] * beamCost + maxSlots[1] * missileCost + maxSlots[2] * hangerCost;
    }

    private int getModuleStatPoint(EModuleType moduleType, EModuleSubType subType) {
        List<ModuleData> modules = getModulesByType(moduleType);
        for (ModuleData data : modules) {
            if (subType.equals(data.getModuleSubType()))
                return data.getStatPoint() != null ? data.getStatPoint() : 0;
        }
        return 0;
    }

    // DataTableShipPreset.json의 statAllocation 노드에서 beam/missile/hanger 각각 비어있지 않은 슬롯만 추출 — modules_in_preset.csv와 동일한 "빈칸=미장착" 규칙
    private java.util.List<DefaultModuleEntry> parseDefaultModules(com.fasterxml.jackson.databind.JsonNode statAllocation) {
        java.util.List<DefaultModuleEntry> result = new java.util.ArrayList<>();
        if (statAllocation == null || statAllocation.isMissingNode()) return result;

        appendDefaultModules(result, statAllocation.path("beamModuleSubType"), EModuleType.beam);
        appendDefaultModules(result, statAllocation.path("missileModuleSubType"), EModuleType.missile);
        appendDefaultModules(result, statAllocation.path("hangarModuleSubType"), EModuleType.hanger);
        return result;
    }

    private void appendDefaultModules(java.util.List<DefaultModuleEntry> result, com.fasterxml.jackson.databind.JsonNode subTypeArray, EModuleType moduleType) {
        if (subTypeArray == null || subTypeArray.isArray() == false) return;
        for (int i = 0; i < subTypeArray.size(); i++) {
            String subTypeName = subTypeArray.get(i).asText("");
            if (subTypeName.isEmpty()) continue;
            try {
                result.add(new DefaultModuleEntry(moduleType, i, EModuleSubType.valueOf(subTypeName)));
            } catch (IllegalArgumentException e) {
                log.warn("[GameDataService] DataTableShipPreset.json에 알 수 없는 moduleSubType '{}' — 무시", subTypeName);
            }
        }
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
