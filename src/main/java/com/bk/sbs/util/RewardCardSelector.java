// 셀 클리어 보상카드 후보 추첨 — 2단계 가중치 랜덤, 중복 없이 N개 선택
// 1단계: effect_type(효과 종류)별로 weight 합을 구해 어느 종류가 나올지 먼저 추첨 — 종류마다 등록된 행(등급) 개수가 달라도
//        (예: 즉시회복처럼 5등급) 그 종류가 뽑힐 확률이 등급 개수에 비례해 커지지 않도록 함
// 2단계: 뽑힌 effect_type 안에서 그 행들의 weight로 다시 추첨해 구체적으로 어느 등급(카드)이 나올지 결정
// 한 종류가 뽑히면 같은 effect_type의 나머지 등급은 다음 후보 추첨에서 전부 제외해 중복을 막음
// 적함대 생성(클라 ExplorationEnemyFleetGenerator, 결정론적 시드 기반)과 달리 유저가 재도전할 이유가 없는 영역이라 결정론적 시드가 필요 없음(java.util.Random으로 충분)
package com.bk.sbs.util;

import com.bk.sbs.service.GameDataService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RewardCardSelector {

    // excludeCardIds: 지금 보여주고 있던 카드ID들 — 같은 effect_type 자체를 배제하진 않고, 정확히 같은 등급(카드)만 이번 추첨에서 제외
    public static List<GameDataService.RewardCardEntry> selectCandidates(List<GameDataService.RewardCardEntry> pool, int count, Random random, List<String> excludeCardIds) {
        List<GameDataService.RewardCardEntry> remaining = new ArrayList<>();
        for (GameDataService.RewardCardEntry entry : pool) {
            if (excludeCardIds != null && excludeCardIds.contains(entry.cardId)) continue;
            remaining.add(entry);
        }
        List<GameDataService.RewardCardEntry> result = new ArrayList<>();

        int distinctEffectTypeCount = countDistinctEffectTypes(remaining);
        int pickCount = Math.min(count, distinctEffectTypeCount);

        for (int i = 0; i < pickCount; i++) {
            String pickedEffectType = pickEffectType(remaining, random);

            List<GameDataService.RewardCardEntry> sameEffectTypeEntries = new ArrayList<>();
            for (GameDataService.RewardCardEntry entry : remaining) {
                if (entry.effectType.equals(pickedEffectType))
                    sameEffectTypeEntries.add(entry);
            }

            GameDataService.RewardCardEntry picked = pickEntryByWeight(sameEffectTypeEntries, random);
            result.add(picked);

            remaining.removeIf(entry -> entry.effectType.equals(pickedEffectType)); // 같은 효과의 다른 등급도 후보에서 제외
        }

        return result;
    }

    private static int countDistinctEffectTypes(List<GameDataService.RewardCardEntry> entries) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (GameDataService.RewardCardEntry entry : entries)
            seen.put(entry.effectType, true);
        return seen.size();
    }

    // effect_type별 weight 합으로 그룹을 만들고, 그 합 비례 확률로 effect_type 하나를 뽑음
    private static String pickEffectType(List<GameDataService.RewardCardEntry> entries, Random random) {
        Map<String, Integer> weightByEffectType = new LinkedHashMap<>();
        for (GameDataService.RewardCardEntry entry : entries) {
            int previousWeight = weightByEffectType.getOrDefault(entry.effectType, 0);
            weightByEffectType.put(entry.effectType, previousWeight + Math.max(entry.weight, 1));
        }

        int totalWeight = 0;
        for (int weight : weightByEffectType.values()) totalWeight += weight;

        int roll = random.nextInt(totalWeight);
        int accumulated = 0;
        for (Map.Entry<String, Integer> group : weightByEffectType.entrySet()) {
            accumulated += group.getValue();
            if (roll < accumulated) return group.getKey();
        }

        return weightByEffectType.keySet().iterator().next(); // 부동소수점 누적오차 등으로 못 걸렸을 때의 방어적 폴백
    }

    // 같은 effect_type 안에서 행(등급)별 weight 비례 확률로 하나를 뽑음
    private static GameDataService.RewardCardEntry pickEntryByWeight(List<GameDataService.RewardCardEntry> entries, Random random) {
        int totalWeight = 0;
        for (GameDataService.RewardCardEntry entry : entries) totalWeight += Math.max(entry.weight, 1);

        int roll = random.nextInt(totalWeight);
        int accumulated = 0;
        for (GameDataService.RewardCardEntry entry : entries) {
            accumulated += Math.max(entry.weight, 1);
            if (roll < accumulated) return entry;
        }

        return entries.get(entries.size() - 1); // 방어적 폴백
    }
}
