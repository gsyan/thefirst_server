package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClaimZoneRewardResponse
 * Auto-generated from Unity C# ClaimZoneRewardResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ClaimZoneRewardResponse {
    private String zoneName;
    private Boolean watchedAd;
    private Integer commanderLevel;
    private Integer totalExp;
}
