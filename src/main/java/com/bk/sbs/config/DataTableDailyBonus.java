package com.bk.sbs.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// DataTableDailyBonus.json 역직렬화 — 일일 보상이 고정 10 exploration point로 변경되어 더 이상 사용되지 않음
// 기존 구조는 유지하되 메서드들은 제거됨
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTableDailyBonus {

    private List<DayConfig> days = new ArrayList<>();

    public DataTableDailyBonus() {}
    public DataTableDailyBonus(List<DayConfig> days) {
        this.days = days != null ? days : new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DayConfig {
        private int day;
        private List<RewardEntry> rewards = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RewardEntry {
        private int amount;
    }
}
