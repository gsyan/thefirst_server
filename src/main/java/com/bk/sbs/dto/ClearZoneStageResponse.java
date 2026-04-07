package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClearZoneStageResponse
 * Auto-generated from Unity C# ClearZoneStageResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ClearZoneStageResponse {
    private CostRemainInfoDto rewardInfo;
    private Boolean isZoneCleared;
    private String clearedZoneName;
    private String collectDateTime;
}
