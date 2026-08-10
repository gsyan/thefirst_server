package com.bk.sbs.util;

import com.bk.sbs.dto.ModuleBodyInfoDto;
import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.ModuleInfoDto;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.service.GameDataService;
import com.bk.sbs.service.GameDataService.DefaultModuleEntry;
import com.bk.sbs.service.GameDataService.ShipPresetSummary;

import java.util.ArrayList;
import java.util.List;

// 셀별 적함대 절차적 생성. presetId 선택(예산 배분) 로직은 구식 DataTableZoneEditor.GenGradePartition과 동일 의도이며
// 클라 Assets/Scripts/Exploration/ExplorationEnemyFleetGenerator.cs와 반드시 동일하게 유지할 것(결정론적 재계산, 두 파일 항상 함께 수정)
// — 실제 장착 모듈(ShipResult.modules)은 ZoneConfigData의 존별 고정 데이터(enemyBeamEquipSlots 등)로 결정론적으로 채워지며 서버 전용이다.
// 클라는 전투 시작(EnterExplorationCellRequest) 응답으로만 이 값을 받아 스폰하므로(UIPanelExplorationGrid),
// 클라 사전 프리뷰(ExplorationEnemyFleetGenerator.GenerateWaves)에는 모듈 구성이 반영되지 않음(프리셋 선택까지만 미리보기)
//
// 예산 소진 순서: (1) 함체(bodyCost, 함선마다 편차 새로 굴림) → (2) 남은 함대 예산으로 빔/미사일/격납고 라운드로빈 구매
// → (3) 함선수 상한/예산 부족으로 루프가 끝나고도 남은 애매한 잔액을 1번 함선부터 다시 훑으며 흡수
public class ZoneEnemyFleetGenerator {

    public static class ShipResult {
        public String presetId;
        public boolean isFront;
        public ModuleBodyInfoDto modules; // 실제 장착 모듈(존 데이터+예산 기반 결정론) — presetId 기본 로드아웃(빔1) + 라운드로빈으로 추가 장착
        public ShipResult(String presetId, boolean isFront, ModuleBodyInfoDto modules) {
            this.presetId = presetId;
            this.isFront = isFront;
            this.modules = modules;
        }
    }

    public static class WaveResult {
        public List<ShipResult> ships = new ArrayList<>();
    }

    // 함선 하나를 만드는 동안 계속 누적되는 임시 상태 — 라운드로빈/잔액흡수 단계에서 공유
    private static class BuildingShip {
        ShipPresetSummary preset;
        List<ModuleInfoDto> beams = new ArrayList<>();
        List<ModuleInfoDto> missiles = new ArrayList<>();
        List<ModuleInfoDto> hangers = new ArrayList<>();
        int beamTarget;
        int missileTarget;
        int hangerTarget;
        String shieldSubType = "";
        String interceptorSubType = "";
    }

    // 존별로 설정된 순차 웨이브(fleets) 전체 생성
    public static List<WaveResult> generateWaves(ZoneConfigData zoneConfig, int seed, int row, int col,
                                                  List<ShipPresetSummary> presets, GameDataService gameDataService) {
        List<WaveResult> waves = new ArrayList<>();
        if (presets == null || presets.isEmpty() || zoneConfig == null) return waves;

        int fleetsPerCell = zoneConfig.getEnemyFleetsPerCell() != null ? zoneConfig.getEnemyFleetsPerCell() : 0;
        for (int fleetIndex = 0; fleetIndex < fleetsPerCell; fleetIndex++)
            waves.add(generateOneWave(seed, row, col, fleetIndex, presets, zoneConfig, gameDataService));

        return waves;
    }

    // 구식 DataTableZoneEditor.GetBlockMaxTier와 동일 공식 — (row,col,fleetIndex,shipIndex) 결정론적, deviation만큼 costCap을 무작위로 낮춤
    // shipIndex를 시드에 포함시켜 함선마다 편차가 다시 굴려지게 함 — 같은 함대예산이라도 판마다 척수/구성이 달라짐
    private static int resolveCostCap(int seed, int row, int col, int fleetIndex, int shipIndex, int maxCost, int deviation) {
        if (deviation <= 0) return maxCost;
        CrossPlatformRandom random = new CrossPlatformRandom(seed ^ (row * 73856093) ^ (col * 19349663) ^ (fleetIndex * 83492791) ^ (shipIndex * 668265261));
        return Math.max(1, maxCost - random.next(0, deviation + 1));
    }

    // 프리셋들 중 (bodyCost + 기본모듈 비용) 최솟값 — 함선 하나를 만드는 데 최소로 필요한 예산. 하드코딩하지 않고 데이터에서 매번 계산
    private static int resolveMinShipCost(List<ShipPresetSummary> presets, GameDataService gameDataService) {
        int minCost = Integer.MAX_VALUE;
        for (ShipPresetSummary preset : presets) {
            int cost = preset.bodyCost + sumDefaultModuleCost(preset, gameDataService);
            if (cost < minCost) minCost = cost;
        }
        return minCost == Integer.MAX_VALUE ? 0 : minCost;
    }

    private static int sumDefaultModuleCost(ShipPresetSummary preset, GameDataService gameDataService) {
        if (preset.defaultModules == null) return 0;
        int sum = 0;
        for (DefaultModuleEntry entry : preset.defaultModules)
            sum += gameDataService.getModuleStatPoint(entry.moduleType, entry.moduleSubType);
        return sum;
    }

    private static WaveResult generateOneWave(int seed, int row, int col, int fleetIndex,
                                               List<ShipPresetSummary> presets, ZoneConfigData zoneConfig,
                                               GameDataService gameDataService) {
        WaveResult waveResult = new WaveResult();
        int maxCostOfOneShip = zoneConfig.getEnemyMaxCostOfOneShip() != null ? zoneConfig.getEnemyMaxCostOfOneShip() : 0;
        int enemyDeviation = zoneConfig.getEnemyDeviation() != null ? zoneConfig.getEnemyDeviation() : 0;
        int enemyBudget = zoneConfig.getEnemyBudget() != null ? zoneConfig.getEnemyBudget() : 0;
        int enemyMaxShipsPerFleet = zoneConfig.getEnemyMaxShipsPerFleet() != null ? zoneConfig.getEnemyMaxShipsPerFleet() : 0;

        CrossPlatformRandom random = new CrossPlatformRandom(seed ^ (row * 73856093) ^ (col * 19349663) ^ (fleetIndex * 83492791) ^ 0x5EED);
        int minShipCost = resolveMinShipCost(presets, gameDataService);

        List<BuildingShip> ships = new ArrayList<>();
        int remaining = enemyBudget;

        // 1) 메인 루프: 함선 단위로 순차 생성, 편차는 함선마다 새로 굴림
        int shipIndex = 0;
        while (remaining >= minShipCost && minShipCost > 0 && ships.size() < enemyMaxShipsPerFleet - 1) {
            int perShipCap = Math.min(remaining, resolveCostCap(seed, row, col, fleetIndex, shipIndex, maxCostOfOneShip, enemyDeviation));
            BuildingShip ship = buildOneShip(presets, perShipCap, zoneConfig, gameDataService, random);
            if (ship == null) break;

            int spent = ship.preset.bodyCost + sumDefaultModuleCost(ship.preset, gameDataService);
            int shipBudget = perShipCap - spent;
            spent += fillRoundRobin(ship, shipBudget, gameDataService, random);

            remaining -= spent;
            ships.add(ship);
            shipIndex++;
        }

        // 2) 함선수 상한 때문에 루프가 끝났고 예산이 남았으면, 남은 예산에 가장 가깝게 맞는(낭비 최소) 함체로 마지막 한 척 확정
        if (remaining >= minShipCost && minShipCost > 0 && ships.size() < enemyMaxShipsPerFleet) {
            BuildingShip lastShip = buildBestFitShip(presets, remaining, zoneConfig, gameDataService);
            if (lastShip != null) {
                int spent = lastShip.preset.bodyCost + sumDefaultModuleCost(lastShip.preset, gameDataService);
                int shipBudget = remaining - spent;
                spent += fillRoundRobin(lastShip, shipBudget, gameDataService, random);
                remaining -= spent;
                ships.add(lastShip);
            }
        }

        if (ships.isEmpty() && !presets.isEmpty()) {
            BuildingShip fallback = newBuildingShip(presets.get(0), zoneConfig); // 폴백 — 구식 "grade=1" 폴백과 동일 의도
            ships.add(fallback);
        }

        // 3) 잔액 흡수 — 1번 함선부터 순서대로 훑으며 남은 예산을 라운드로빈으로 마저 채움
        remaining = absorbLeftover(ships, remaining, zoneConfig, gameDataService, random);

        return finalizeWave(waveResult, ships);
    }

    // 예산 상한(perShipCap) 안에서 함체를 랜덤 선택 — 함체가 여러 등급이면 항상 제일 비싼 것만 고르지 않도록 랜덤 어포더블 방식 유지
    private static BuildingShip buildOneShip(List<ShipPresetSummary> presets, int cap, ZoneConfigData zoneConfig,
                                              GameDataService gameDataService, CrossPlatformRandom random) {
        List<ShipPresetSummary> affordable = new ArrayList<>();
        for (ShipPresetSummary preset : presets) {
            int minCostForPreset = preset.bodyCost + sumDefaultModuleCost(preset, gameDataService);
            if (preset.bodyCost > 0 && minCostForPreset <= cap)
                affordable.add(preset);
        }
        if (affordable.isEmpty()) return null;

        ShipPresetSummary chosen = affordable.get(random.next(affordable.size()));
        return newBuildingShip(chosen, zoneConfig);
    }

    // 남은 예산에 가장 가깝게 맞는(낭비 최소) 함체 — 마지막 한 척 확정용, 의도적으로 그리디
    private static BuildingShip buildBestFitShip(List<ShipPresetSummary> presets, int cap, ZoneConfigData zoneConfig,
                                                  GameDataService gameDataService) {
        ShipPresetSummary bestFit = null;
        for (ShipPresetSummary preset : presets) {
            int minCostForPreset = preset.bodyCost + sumDefaultModuleCost(preset, gameDataService);
            if (preset.bodyCost > 0 && minCostForPreset <= cap
                    && (bestFit == null || preset.bodyCost > bestFit.bodyCost))
                bestFit = preset;
        }
        return bestFit == null ? null : newBuildingShip(bestFit, zoneConfig);
    }

    private static BuildingShip newBuildingShip(ShipPresetSummary preset, ZoneConfigData zoneConfig) {
        BuildingShip ship = new BuildingShip();
        ship.preset = preset;

        if (preset.defaultModules != null) {
            for (DefaultModuleEntry entry : preset.defaultModules) {
                ModuleInfoDto dto = ModuleInfoDto.builder()
                        .moduleType(entry.moduleType)
                        .moduleSubType(entry.moduleSubType)
                        .slotIndex(entry.slotIndex)
                        .build();
                addToCategory(ship, entry.moduleType, dto);
            }
        }

        int beamEquipSlots    = zoneConfig.getEnemyBeamEquipSlots()    != null ? zoneConfig.getEnemyBeamEquipSlots()    : 0;
        int missileEquipSlots = zoneConfig.getEnemyMissileEquipSlots() != null ? zoneConfig.getEnemyMissileEquipSlots() : 0;
        int hangerEquipSlots  = zoneConfig.getEnemyHangerEquipSlots()  != null ? zoneConfig.getEnemyHangerEquipSlots()  : 0;
        ship.beamTarget    = Math.min(beamEquipSlots, preset.maxSlots[0]);
        ship.missileTarget = Math.min(missileEquipSlots, preset.maxSlots[1]);
        ship.hangerTarget  = Math.min(hangerEquipSlots, preset.maxSlots[2]);

        // 실드/인터셉터 — 슬롯 1개뿐이라 "장착 여부"만 존재. 클라 배관(ModuleBodyInfo.shieldModuleSubType 등)만 채우고
        // 클라이언트가 실제로 소비(스탯 반영/스폰)하는 로직은 아직 없음 — 후속 작업
        int shieldEquipSlots      = zoneConfig.getEnemyShieldEquipSlots()      != null ? zoneConfig.getEnemyShieldEquipSlots()      : 0;
        int interceptorEquipSlots = zoneConfig.getEnemyInterceptorEquipSlots() != null ? zoneConfig.getEnemyInterceptorEquipSlots() : 0;
        ship.shieldSubType = preset.maxSlots[3] > 0 && shieldEquipSlots > 0 ? EModuleSubType.shield_t1.name() : "";
        ship.interceptorSubType = preset.maxSlots[4] > 0 && interceptorEquipSlots > 0 ? EModuleSubType.interceptor_t1.name() : "";
        return ship;
    }

    // shipBudget(이 함선에 배정된 여유분)과 fleet 공유 remaining 둘 다 한도로, 빔→미사일→격납고 순서로 한 슬롯씩 라운드로빈 구매.
    // 실제 소비한 총액을 반환.
    private static int fillRoundRobin(BuildingShip ship, int shipBudget, GameDataService gameDataService, CrossPlatformRandom random) {
        int spent = 0;
        boolean progressed = true;
        while (progressed && spent < shipBudget) {
            progressed = false;
            for (EModuleType category : new EModuleType[] { EModuleType.beam, EModuleType.missile, EModuleType.hanger }) {
                if (hasSlotRoom(ship, category) == false) continue;
                Integer cost = tryEquipOneModule(ship, category, shipBudget - spent, gameDataService, random);
                if (cost != null) {
                    spent += cost;
                    progressed = true;
                }
            }
        }
        return spent;
    }

    // 메인 루프/마지막 척으로도 다 못 쓴 함대 예산을 1번 함선부터 순서대로 훑으며 흡수 — 한 척에만 몰아주지 않음
    private static int absorbLeftover(List<BuildingShip> ships, int remaining, ZoneConfigData zoneConfig,
                                       GameDataService gameDataService, CrossPlatformRandom random) {
        boolean progressed = true;
        while (remaining > 0 && progressed) {
            progressed = false;
            for (BuildingShip ship : ships) {
                for (EModuleType category : new EModuleType[] { EModuleType.beam, EModuleType.missile, EModuleType.hanger }) {
                    if (hasSlotRoom(ship, category) == false) continue;
                    Integer cost = tryEquipOneModule(ship, category, remaining, gameDataService, random);
                    if (cost != null) {
                        remaining -= cost;
                        progressed = true;
                    }
                }
            }
        }
        return remaining;
    }

    private static boolean hasSlotRoom(BuildingShip ship, EModuleType category) {
        return switch (category) {
            case beam -> ship.beams.size() < ship.beamTarget;
            case missile -> ship.missiles.size() < ship.missileTarget;
            case hanger -> ship.hangers.size() < ship.hangerTarget;
            default -> false;
        };
    }

    // 해당 카테고리에서 budget 이하로 살 수 있는 서브타입 중 랜덤 선택해 장착 — 지금은 t1 하나뿐이라 결과는 동일하지만,
    // 나중에 모듈 등급이 늘어도 함체 선택과 동일한 "랜덤 어포더블" 패턴이라 코드 변경 없이 등급이 섞임
    private static Integer tryEquipOneModule(BuildingShip ship, EModuleType category, int budget,
                                              GameDataService gameDataService, CrossPlatformRandom random) {
        List<ModuleData> candidates = new ArrayList<>();
        for (ModuleData data : gameDataService.getModulesByType(category)) {
            int cost = data.getStatPoint() != null ? data.getStatPoint() : 0;
            if (cost > 0 && cost <= budget && isAlreadyEquipped(ship, category, data.getModuleSubType()) == false)
                candidates.add(data);
        }
        if (candidates.isEmpty()) return null;

        ModuleData chosen = candidates.get(random.next(candidates.size()));
        int slotIndex = nextFreeSlotIndex(ship, category);
        ModuleInfoDto dto = ModuleInfoDto.builder()
                .moduleType(category)
                .moduleSubType(chosen.getModuleSubType())
                .slotIndex(slotIndex)
                .build();
        addToCategory(ship, category, dto);
        return chosen.getStatPoint();
    }

    // 같은 슬롯 인덱스 중복 방지용 — 물리 슬롯 수(maxSlots)를 넘지 않는 범위에서 비어있는 가장 낮은 인덱스
    private static int nextFreeSlotIndex(BuildingShip ship, EModuleType category) {
        List<ModuleInfoDto> list = categoryList(ship, category);
        boolean[] used = new boolean[ship.preset.maxSlots[categoryOrdinal(category)]];
        for (ModuleInfoDto dto : list) {
            if (dto.getSlotIndex() >= 0 && dto.getSlotIndex() < used.length) used[dto.getSlotIndex()] = true;
        }
        for (int i = 0; i < used.length; i++)
            if (used[i] == false) return i;
        return list.size();
    }

    private static boolean isAlreadyEquipped(BuildingShip ship, EModuleType category, EModuleSubType subType) {
        for (ModuleInfoDto dto : categoryList(ship, category))
            if (dto.getModuleSubType() == subType) return true;
        return false;
    }

    private static int categoryOrdinal(EModuleType category) {
        return switch (category) {
            case beam -> 0;
            case missile -> 1;
            case hanger -> 2;
            default -> 0;
        };
    }

    private static List<ModuleInfoDto> categoryList(BuildingShip ship, EModuleType category) {
        return switch (category) {
            case beam -> ship.beams;
            case missile -> ship.missiles;
            case hanger -> ship.hangers;
            default -> new ArrayList<>();
        };
    }

    private static void addToCategory(BuildingShip ship, EModuleType moduleType, ModuleInfoDto dto) {
        switch (moduleType) {
            case beam -> ship.beams.add(dto);
            case missile -> ship.missiles.add(dto);
            case hanger -> ship.hangers.add(dto);
            default -> { }
        }
    }

    // 장착 모듈 개수 내림차순으로 정렬해 절반은 전방/절반은 후방 — List.sort는 안정 정렬(동률 시 삽입 순서 보존) 보장
    private static WaveResult finalizeWave(WaveResult waveResult, List<BuildingShip> ships) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ships.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(equippedCount(ships.get(b)), equippedCount(ships.get(a))));

        for (int i = 0; i < order.size(); i++) {
            int idx = order.get(i);
            BuildingShip ship = ships.get(idx);
            boolean isFront = i < (order.size() + 1) / 2;

            ModuleBodyInfoDto modules = ModuleBodyInfoDto.builder()
                    .beams(ship.beams).missiles(ship.missiles).hangers(ship.hangers)
                    .shieldModuleSubType(ship.shieldSubType)
                    .interceptorModuleSubType(ship.interceptorSubType)
                    .build();
            waveResult.ships.add(new ShipResult(ship.preset.presetId, isFront, modules));
        }
        return waveResult;
    }

    private static int equippedCount(BuildingShip ship) {
        return ship.beams.size() + ship.missiles.size() + ship.hangers.size();
    }
}
