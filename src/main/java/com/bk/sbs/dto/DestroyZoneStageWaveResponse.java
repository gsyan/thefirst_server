package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DestroyZoneStageWaveResponse
 * Auto-generated from Unity C# DestroyZoneStageWaveResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DestroyZoneStageWaveResponse {
    private CostRemainInfoDto rewardInfo;
    private Boolean isZoneCleared;
    private String clearedZoneName;
    private String collectDateTime;
}
