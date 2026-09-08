package com.bk.sbs.config;

import com.bk.sbs.enums.EDailyBonusRewardType;
import com.bk.sbs.enums.EDailyBonusTier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 클라이언트 DataTableDailyBonus.asset을 Export JSON으로 내보낸 DataTableDailyBonus.json 역직렬화
// day별로 (tier, rewardType, amount) 보상 목록을 담음 — IapService.claimDailyReward()의 지급액 조회 원본
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTableDailyBonus {

    private List<DayConfig> days = new ArrayList<>();

    public DataTableDailyBonus() {}
    public DataTableDailyBonus(List<DayConfig> days) {
        this.days = days != null ? days : new ArrayList<>();
    }

    public List<RewardEntry> getRewards(int day, EDailyBonusTier tier) {
        for (DayConfig dayConfig : days) {
            if (dayConfig.getDay() != day) continue;

            List<RewardEntry> filtered = new ArrayList<>();
            for (RewardEntry reward : dayConfig.getRewards()) {
                if (reward.getTier() == tier)
                    filtered.add(reward);
            }
            return filtered;
        }
        return new ArrayList<>();
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
