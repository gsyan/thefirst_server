package com.bk.sbs.util;

import com.bk.sbs.dto.ModuleBodyInfoDto;
import com.bk.sbs.dto.ModuleInfoDto;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.service.GameDataService.DefaultModuleEntry;
import com.bk.sbs.service.GameDataService.ShipPresetSummary;

import java.util.ArrayList;
import java.util.List;

// 셀별 적함대 절차적 생성. presetId 선택(예산 배분) 로직은 구식 DataTableZoneEditor.GenGradePartition과 동일 의도이며
// 클라 Assets/Scripts/Exploration/ExplorationEnemyFleetGenerator.cs와 반드시 동일하게 유지할 것(결정론적 재계산, 두 파일 항상 함께 수정)
// — 단, 실제 장착 모듈(ShipResult.modules) 랜덤 다양성은 서버 전용이며 클라에는 포팅하지 않는다. 클라는 전투 시작(EnterExplorationCellRequest)
// 응답으로만 이 값을 받아 스폰하므로(UIPanelExplorationGrid), 클라 사전 프리뷰(ExplorationEnemyFleetGenerator.GenerateWaves)에는 모듈 다양성이 반영되지 않음
public class ZoneEnemyFleetGenerator {

    // 카테고리 슬롯 하나를 추가 장착할 확률 — 기본 로드아웃(빔 slot0) 이외의 슬롯에만 적용
    private static final int k_extraModuleChancePercent = 50;

    public static class ShipResult {
        public String presetId;
        public boolean isFront;
        public ModuleBodyInfoDto modules; // 실제 장착 모듈(랜덤) — presetId 기본 로드아웃(빔1) + fullEquipCost 예산 안에서 추가 장착
        public ShipResult(String presetId, boolean isFront, ModuleBodyInfoDto modules) {
            this.presetId = presetId;
            this.isFront = isFront;
            this.modules = modules;
        }
    }

    public static class WaveResult {
        public List<ShipResult> ships = new ArrayList<>();
    }

    // 존별로 설정된 순차 웨이브(fleets) 전체 생성
    public static List<WaveResult> generateWaves(ZoneConfigData zoneConfig, int seed, int row, int col,
                                                  List<ShipPresetSummary> presets) {
        List<WaveResult> waves = new ArrayList<>();
        if (presets == null || presets.isEmpty() || zoneConfig == null) return waves;

        int fleetsPerCell = zoneConfig.getEnemyFleetsPerCell() != null ? zoneConfig.getEnemyFleetsPerCell() : 0;
        for (int fleetIndex = 0; fleetIndex < fleetsPerCell; fleetIndex++)
            waves.add(generateOneWave(seed, row, col, fleetIndex, presets, zoneConfig));

        return waves;
    }

    // 구식 DataTableZoneEditor.GetBlockMaxTier와 동일 공식 — (row,col,fleetIndex) 결정론적, deviation만큼 costCap을 무작위로 낮춤
    private static int resolveCostCap(int seed, int row, int col, int fleetIndex, int maxCost, int deviation) {
        if (deviation <= 0) return maxCost;
        CrossPlatformRandom random = new CrossPlatformRandom(seed ^ (row * 73856093) ^ (col * 19349663) ^ (fleetIndex * 83492791));
        return Math.max(1, maxCost - random.next(0, deviation + 1));
    }

    // 예산을 무작위로 소진하되, 함선 수 상한에 걸리면 마지막 한 척은 "남은 예산에 가장 가까운(낭비 최소)" 프리셋으로 확정해서 채움
    // 예산 비교는 항상 fullEquipCost(모든 슬롯 완전장착 기준) — 이후 실제 장착은 그 예산 안에서 랜덤으로 일부만 채우므로 과소비가 절대 없음
    private static WaveResult generateOneWave(int seed, int row, int col, int fleetIndex,
                                               List<ShipPresetSummary> presets, ZoneConfigData zoneConfig) {
        WaveResult waveResult = new WaveResult();
        int enemyMaxCost = zoneConfig.getEnemyMaxCost() != null ? zoneConfig.getEnemyMaxCost() : 0;
        int enemyDeviation = zoneConfig.getEnemyDeviation() != null ? zoneConfig.getEnemyDeviation() : 0;
        int enemyBudget = zoneConfig.getEnemyBudget() != null ? zoneConfig.getEnemyBudget() : 0;
        int enemyMaxShipsPerFleet = zoneConfig.getEnemyMaxShipsPerFleet() != null ? zoneConfig.getEnemyMaxShipsPerFleet() : 0;

        int costCap = resolveCostCap(seed, row, col, fleetIndex, enemyMaxCost, enemyDeviation);
        CrossPlatformRandom random = new CrossPlatformRandom(seed ^ (row * 73856093) ^ (col * 19349663) ^ (fleetIndex * 83492791) ^ 0x5EED);

        int remaining = enemyBudget;
        List<ShipPresetSummary> affordable = new ArrayList<>();
        List<ShipPresetSummary> chosenShips = new ArrayList<>();

        while (remaining > 0 && chosenShips.size() < enemyMaxShipsPerFleet - 1) {
            int cap = Math.min(remaining, costCap);
            affordable.clear();
            for (ShipPresetSummary preset : presets)
                if (preset.fullEquipCost > 0 && preset.fullEquipCost <= cap)
                    affordable.add(preset);
            if (affordable.isEmpty()) break;

            ShipPresetSummary chosen = affordable.get(random.next(affordable.size()));
            chosenShips.add(chosen);
            remaining -= chosen.fullEquipCost;
        }

        // 함선 수 상한에 걸려 예산이 남았으면, 남은 예산에 가장 가깝게 맞는(낭비 최소) 프리셋으로 마지막 한 척 채움
        if (remaining > 0) {
            int cap = Math.min(remaining, costCap);
            ShipPresetSummary bestFit = null;
            for (ShipPresetSummary preset : presets) {
                if (preset.fullEquipCost > 0 && preset.fullEquipCost <= cap
                        && (bestFit == null || preset.fullEquipCost > bestFit.fullEquipCost))
                    bestFit = preset;
            }
            if (bestFit != null)
                chosenShips.add(bestFit);
        }

        if (chosenShips.isEmpty() && !presets.isEmpty())
            chosenShips.add(presets.get(0)); // 폴백 — 구식 "grade=1" 폴백과 동일 의도

        // 각 함선의 실제 장착 모듈을 굴린 뒤, 실제 장착 코스트 내림차순으로 정렬해 절반은 전방/절반은 후방
        // List.sort는 안정 정렬(동률 시 삽입 순서 보존) 보장
        List<ModuleBodyInfoDto> rolledModules = new ArrayList<>();
        for (ShipPresetSummary preset : chosenShips)
            rolledModules.add(rollModules(preset, random));

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < chosenShips.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(
                computeEquippedCost(chosenShips.get(b), rolledModules.get(b)),
                computeEquippedCost(chosenShips.get(a), rolledModules.get(a))));

        for (int i = 0; i < order.size(); i++) {
            int idx = order.get(i);
            boolean isFront = i < (order.size() + 1) / 2;
            waveResult.ships.add(new ShipResult(chosenShips.get(idx).presetId, isFront, rolledModules.get(idx)));
        }

        return waveResult;
    }

    // 기본 로드아웃(빔 slot0)은 항상 장착, 나머지 슬롯은 카테고리별로 일정 확률로 랜덤 장착 — fullEquipCost 예산 안에서 이미 선택됐으므로 전부 켜져도 초과 없음
    private static ModuleBodyInfoDto rollModules(ShipPresetSummary preset, CrossPlatformRandom random) {
        List<ModuleInfoDto> beams = new ArrayList<>();
        List<ModuleInfoDto> missiles = new ArrayList<>();
        List<ModuleInfoDto> hangers = new ArrayList<>();

        if (preset.defaultModules != null) {
            for (DefaultModuleEntry entry : preset.defaultModules) {
                ModuleInfoDto dto = ModuleInfoDto.builder()
                        .moduleType(entry.moduleType)
                        .moduleSubType(entry.moduleSubType)
                        .slotIndex(entry.slotIndex)
                        .build();
                addToCategory(beams, missiles, hangers, entry.moduleType, dto);
            }
        }

        rollExtraSlots(beams, EModuleType.beam, EModuleSubType.beam_t1, preset.maxSlots[0], preset.defaultModules, random);
        rollExtraSlots(missiles, EModuleType.missile, EModuleSubType.missile_t1, preset.maxSlots[1], preset.defaultModules, random);
        rollExtraSlots(hangers, EModuleType.hanger, EModuleSubType.hanger_t1, preset.maxSlots[2], preset.defaultModules, random);

        return ModuleBodyInfoDto.builder().beams(beams).missiles(missiles).hangers(hangers).build();
    }

    private static void addToCategory(List<ModuleInfoDto> beams, List<ModuleInfoDto> missiles, List<ModuleInfoDto> hangers, EModuleType moduleType, ModuleInfoDto dto) {
        switch (moduleType) {
            case beam -> beams.add(dto);
            case missile -> missiles.add(dto);
            case hanger -> hangers.add(dto);
            default -> { }
        }
    }

    private static void rollExtraSlots(List<ModuleInfoDto> target, EModuleType moduleType, EModuleSubType defaultSubType,
                                        int maxSlotCount, List<DefaultModuleEntry> defaultModules, CrossPlatformRandom random) {
        for (int slotIndex = 0; slotIndex < maxSlotCount; slotIndex++) {
            if (isDefaultSlot(defaultModules, moduleType, slotIndex)) continue;
            if (random.next(100) >= k_extraModuleChancePercent) continue;

            target.add(ModuleInfoDto.builder()
                    .moduleType(moduleType)
                    .moduleSubType(defaultSubType)
                    .slotIndex(slotIndex)
                    .build());
        }
    }

    private static boolean isDefaultSlot(List<DefaultModuleEntry> defaultModules, EModuleType moduleType, int slotIndex) {
        if (defaultModules == null) return false;
        for (DefaultModuleEntry entry : defaultModules) {
            if (entry.moduleType == moduleType && entry.slotIndex == slotIndex) return true;
        }
        return false;
    }

    private static int computeEquippedCost(ShipPresetSummary preset, ModuleBodyInfoDto modules) {
        // 정렬용 근사치 — 실제 지휘력 계산(GameDataService 조회)까지는 필요 없고, 장착 슬롯 "개수"만으로도 전/후방 순서 목적엔 충분
        int count = 0;
        if (modules.getBeams() != null) count += modules.getBeams().size();
        if (modules.getMissiles() != null) count += modules.getMissiles().size();
        if (modules.getHangers() != null) count += modules.getHangers().size();
        return count;
    }
}
