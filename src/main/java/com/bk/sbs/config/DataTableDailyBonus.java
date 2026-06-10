package com.bk.sbs.config;

import com.bk.sbs.enums.EDailyBonusRewardType;
import com.bk.sbs.enums.EDailyBonusTier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// DataTableDailyBonus.json 역직렬화 — 클라 DataTableDailyBonus ScriptableObject와 동일 구조
// JSON 최상위가 배열이므로 GameDataService에서 List<DayConfig>로 파싱 후 주입
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTableDailyBonus {

    private List<DayConfig> days = new ArrayList<>();

    public DataTableDailyBonus() {}
    public DataTableDailyBonus(List<DayConfig> days) {
        this.days = days != null ? days : new ArrayList<>();
    }

    // tier 에 해당하는 day의 Mineral 합산. 없으면 -1
    public int getMineralForDay(int day, EDailyBonusTier tier) {
        for (DayConfig config : days) {
            if (config.getDay() == day) {
                int total = 0;
                for (RewardEntry entry : config.getRewards()) {
                    if (entry.getTier() == tier && entry.getRewardType() == EDailyBonusRewardType.Mineral)
                        total += entry.getAmount();
                }
                return total > 0 ? total : -1;
            }
        }
        return -1;
    }

    // fromDay~toDay 구간 VIP Mineral 합산 (catch-up용)
    public int getVipMineralCatchup(int fromDay, int toDay) {
        int total = 0;
        for (int d = fromDay; d <= toDay; d++) {
            int vip = getMineralForDay(d, EDailyBonusTier.VIP);
            if (vip > 0) total += vip;
        }
        return total;
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
        private EDailyBonusTier tier;
        private EDailyBonusRewardType rewardType;
        private int amount;
    }
}
