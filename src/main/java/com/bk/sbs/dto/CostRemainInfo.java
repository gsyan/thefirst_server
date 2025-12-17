package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostRemainInfo {
    private long mineralCost;
    private long mineralRareCost;
    private long mineralExoticCost;
    private long mineralDarkCost;

    private long remainMineral;
    private long remainMineralRare;
    private long remainMineralExotic;
    private long remainMineralDark;
}