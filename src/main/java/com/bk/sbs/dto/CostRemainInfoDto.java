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
    private Integer techPointCost;
    private Integer techPointRemain;
    private Integer modulePointCost;
    private Integer modulePointRemain;
}
