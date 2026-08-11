// 셀 클리어 보상카드 후보 추첨 — weight 기반 가중치 랜덤, 중복 없이 N개 선택
// 적함대 생성(ZoneEnemyFleetGenerator)과 달리 유저가 재도전할 이유가 없는 영역이라 결정론적 시드가 필요 없음(java.util.Random으로 충분)
package com.bk.sbs.util;

import com.bk.sbs.service.GameDataService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RewardCardSelector {

    // pool에서 weight 비례 확률로 중복 없이 count개를 뽑는다. pool 크기가 count보다 작으면 pool 전체를 반환
    public static List<GameDataService.RewardCardEntry> selectCandidates(List<GameDataService.RewardCardEntry> pool, int count, Random random) {
        List<GameDataService.RewardCardEntry> remaining = new ArrayList<>(pool);
        List<GameDataService.RewardCardEntry> result = new ArrayList<>();

        int pickCount = Math.min(count, remaining.size());
        for (int i = 0; i < pickCount; i++) {
            int totalWeight = 0;
            for (GameDataService.RewardCardEntry entry : remaining) {
                totalWeight += Math.max(entry.weight, 1);
            }

            int roll = random.nextInt(totalWeight);
            int accumulated = 0;
            int pickedIndex = 0;
            for (int j = 0; j < remaining.size(); j++) {
                accumulated += Math.max(remaining.get(j).weight, 1);
                if (roll < accumulated) {
                    pickedIndex = j;
                    break;
                }
            }

            result.add(remaining.remove(pickedIndex));
        }

        return result;
    }
}
