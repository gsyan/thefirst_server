// 사령관 레벨업 판정 공용 유틸 — ExplorationService/ZoneService에서 공통 사용
package com.bk.sbs.util;

import com.bk.sbs.entity.Commander;
import com.bk.sbs.service.GameDataService;

public class CommanderLevelUtil {

    // exp 누적 기준으로 레벨업 조건 판정 후 자동 승급 (연속 레벨업 지원, 레벨업 모듈포인트 보상 개념은 삭제됨)
    public static void autoLevelUpIfNeeded(Commander commander, GameDataService gameDataService) {
        int currentLevel = commander.getCommanderLevel();
        int accumulatedExp = commander.getExp();
        int nextLevel = currentLevel + 1;
        int requiredExp = gameDataService.getCommanderLevelRequiredExp(nextLevel);
        while (requiredExp > 0 && accumulatedExp >= requiredExp) {
            currentLevel = nextLevel;
            nextLevel = currentLevel + 1;
            requiredExp = gameDataService.getCommanderLevelRequiredExp(nextLevel);
        }
        commander.setCommanderLevel(currentLevel);
    }
}
