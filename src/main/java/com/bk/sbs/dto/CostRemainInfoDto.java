package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CostRemainInfoDto
 * Auto-generated from Unity C# CostRemainInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CostRemainInfoDto {
    private Long mineralCost;
    private Long mineralRareCost;
    private Long mineralExoticCost;
    private Long mineralDarkCost;
    private Long remainMineral;
    private Long remainMineralRare;
    private Long remainMineralExotic;
    private Long remainMineralDark;
}
