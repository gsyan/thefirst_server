package com.bk.sbs.util;

import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.service.GameDataService.ShipPresetSummary;

import java.util.ArrayList;
import java.util.List;

// 클라 Assets/Scripts/Exploration/ExplorationEnemyFleetGenerator.cs를 그대로 포팅 — 서버가 클라와 동일한 셀 적함대를
// (seed, row, col, fleetIndex) 조합만으로 재계산할 수 있어야 클리어 보상을 결과값(파괴 함선 수/commandCost)으로 검증 가능함.
// CrossPlatformRandom을 함께 써야 하며, 두 파일은 항상 함께 수정할 것.
public class ZoneEnemyFleetGenerator {

    public static class ShipResult {
        public String presetId;
        public boolean isFront;
        public ShipResult(String presetId, boolean isFront) {
            this.presetId = presetId;
            this.isFront = isFront;
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
                if (preset.commandCost > 0 && preset.commandCost <= cap)
                    affordable.add(preset);
            if (affordable.isEmpty()) break;

            ShipPresetSummary chosen = affordable.get(random.next(affordable.size()));
            chosenShips.add(chosen);
            remaining -= chosen.commandCost;
        }

        // 함선 수 상한에 걸려 예산이 남았으면, 남은 예산에 가장 가깝게 맞는(낭비 최소) 프리셋으로 마지막 한 척 채움
        if (remaining > 0) {
            int cap = Math.min(remaining, costCap);
            ShipPresetSummary bestFit = null;
            for (ShipPresetSummary preset : presets) {
                if (preset.commandCost > 0 && preset.commandCost <= cap
                        && (bestFit == null || preset.commandCost > bestFit.commandCost))
                    bestFit = preset;
            }
            if (bestFit != null)
                chosenShips.add(bestFit);
        }

        if (chosenShips.isEmpty() && !presets.isEmpty())
            chosenShips.add(presets.get(0)); // 폴백 — 구식 "grade=1" 폴백과 동일 의도

        // commandCost 내림차순 정렬 후 절반은 전방/절반은 후방
        // List.sort는 안정 정렬(동률 시 삽입 순서 보존) 보장 — 클라(C# OrderByDescending)도 반드시 안정 정렬만 사용할 것
        chosenShips.sort((a, b) -> Integer.compare(b.commandCost, a.commandCost));
        for (int i = 0; i < chosenShips.size(); i++) {
            boolean isFront = i < (chosenShips.size() + 1) / 2;
            waveResult.ships.add(new ShipResult(chosenShips.get(i).presetId, isFront));
        }

        return waveResult;
    }
}
