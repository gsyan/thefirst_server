package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ShipHealthRatioInfoDto
 * Auto-generated from Unity C# ShipHealthRatioInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ShipHealthRatioInfoDto {
    private Long shipId;
    private Integer positionIndex;
    private Float healthRatio;
    private Float shieldRatio;
}
