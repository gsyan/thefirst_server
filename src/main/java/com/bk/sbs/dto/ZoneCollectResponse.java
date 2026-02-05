package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneCollectResponse
 * Auto-generated from Unity C# ZoneCollectResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneCollectResponse {
    private String collectDateTime;
    private CostRemainInfoDto rewardInfo;
}
