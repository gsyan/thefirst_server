package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneClearResponse
 * Auto-generated from Unity C# ZoneClearResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneClearResponse {
    private String clearedZone;
    private CostRemainInfoDto rewardInfo;
    private String collectDateTime;
}
