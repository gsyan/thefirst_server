package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VipDailyMineralResponse
 * Auto-generated from Unity C# VipDailyMineralResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class VipDailyMineralResponse {
    private Boolean available;
    private Integer grantedMineral;
    private Integer mineralRemain;
    private String nextAvailableAt;
}
