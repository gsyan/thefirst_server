package com.bk.sbs.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// DataTablePvpSeason.json 역직렬화 — 클라 DataTablePvpSeason ScriptableObject와 동일 구조
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTablePvpSeason {

    private List<TierEntry> tiers = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TierEntry {
        private String tierName;
        private int minScore;
        private int resetScore;
        private int seasonReward;
    }

    // 점수에 해당하는 티어 반환 (minScore 이상인 최고 티어)
    public TierEntry getTierByScore(int score) {
        TierEntry result = null;
        for (TierEntry tier : tiers) {
            if (score >= tier.getMinScore()) {
                result = tier;
            }
        }
        return result;
    }

    // 점수에 해당하는 리셋 점수 반환 (티어 없으면 기본 1000)
    public int getResetScore(int score) {
        TierEntry tier = getTierByScore(score);
        return tier != null ? tier.getResetScore() : 1000;
    }

    // 점수에 해당하는 시즌 보상 반환 (티어 없으면 2)
    public int getSeasonReward(int score) {
        TierEntry tier = getTierByScore(score);
        return tier != null ? tier.getSeasonReward() : 2;
    }
}
