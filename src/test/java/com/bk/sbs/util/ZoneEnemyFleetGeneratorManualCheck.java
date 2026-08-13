package com.bk.sbs.util;

import com.bk.sbs.dto.ModuleInfoDto;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.service.GameDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// 서버를 실제로 기동하지 않고(DB/Redis 없이) ZoneEnemyFleetGenerator를 실제 데이터(resources/data/*.json)로 돌려
// 어떤 그리드 셀에서 어떤 적함대 구성이 나오는지 콘솔로 확인하기 위한 수동 검증용 테스트.
// 자동 검증(assert)이 아니라 눈으로 확인하는 용도 — 필요 없어지면 삭제해도 무방.
public class ZoneEnemyFleetGeneratorManualCheck {

    @Configuration
    static class ManualCheckConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        GameDataService gameDataService() {
            return new GameDataService();
        }
    }

    @Test
    void printFleetCompositions() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ManualCheckConfig.class)) {
            GameDataService gameDataService = ctx.getBean(GameDataService.class);

            // zoneNumber, seed는 필요에 따라 바꿔가며 확인
            int[] zoneNumbers = { 1 };
            int seedBase = 20260722;

            for (int zoneNumber : zoneNumbers) {
                ZoneConfigData zoneConfig = gameDataService.getZoneConfigByIndex(zoneNumber);
                if (zoneConfig == null) {
                    System.out.println("zone " + zoneNumber + " config not found");
                    continue;
                }

                int seed = seedBase ^ (zoneNumber * 486187739); // ExplorationService.computeZoneSeed와 동일 공식
                int width = zoneConfig.getGridWidth() != null ? zoneConfig.getGridWidth() : 0;
                int height = zoneConfig.getGridHeight() != null ? zoneConfig.getGridHeight() : 0;

                System.out.println("===== zone " + zoneNumber + " (budget=" + zoneConfig.getEnemyBudget()
                        + ", maxCostOfOneShip=" + zoneConfig.getEnemyMaxCostOfOneShip()
                        + ", deviation=" + zoneConfig.getEnemyDeviation()
                        + ", healthMultiplier=" + zoneConfig.getEnemyHealthMultiplier()
                        + ", attackMultiplier=" + zoneConfig.getEnemyAttackMultiplier() + ") =====");

                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        List<ZoneEnemyFleetGenerator.WaveResult> waves = ZoneEnemyFleetGenerator.generateWaves(
                                zoneConfig, seed, row, col, gameDataService.getShipPresetList(), gameDataService);
                        printCell(row, col, waves, gameDataService);
                    }
                }
            }
        }
    }

    private void printCell(int row, int col, List<ZoneEnemyFleetGenerator.WaveResult> waves, GameDataService gameDataService) {
        for (int waveIndex = 0; waveIndex < waves.size(); waveIndex++) {
            ZoneEnemyFleetGenerator.WaveResult wave = waves.get(waveIndex);
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(row).append(",").append(col).append("] wave").append(waveIndex)
                    .append(" ships=").append(wave.ships.size());

            int totalSpent = 0;
            for (ZoneEnemyFleetGenerator.ShipResult ship : wave.ships) {
                GameDataService.ShipPresetSummary preset = gameDataService.getShipPresetSummary(ship.presetId);
                int beamCount = ship.modules.getBeams() != null ? ship.modules.getBeams().size() : 0;
                int missileCount = ship.modules.getMissiles() != null ? ship.modules.getMissiles().size() : 0;
                int hangarCount = ship.modules.getHangars() != null ? ship.modules.getHangars().size() : 0;

                int shipSpent = preset.bodyCost + (beamCount + missileCount + hangarCount) * 20; // 지금은 모듈 전부 t1=20이라 근사 출력용
                totalSpent += shipSpent;

                sb.append(" | ").append(ship.presetId).append("(body=").append(preset.bodyCost)
                        .append(", front=").append(ship.isFront)
                        .append(", beam=").append(beamCount)
                        .append(", missile=").append(missileCount)
                        .append(", hangar=").append(hangarCount)
                        .append(", spent~=").append(shipSpent).append(")");
            }
            sb.append(" totalSpent~=").append(totalSpent);
            System.out.println(sb);
        }
    }
}
