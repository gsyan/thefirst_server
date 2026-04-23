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
    private Integer mineralCost;
    private Integer mineralRemain;
    private Integer pvpMineralCost;
    private Integer pvpMineralRemain;
    private Integer tempMineralCost;
    private Integer tempMineralRemain;
}
