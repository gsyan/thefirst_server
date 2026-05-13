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
    private Boolean isZoneCleared;
    private String clearedZoneName;
    private Integer mineralRemain;
    private Integer techPointRemain;
    private Integer modulePointRemain;
    private Integer modulePointMaxGot;
}
