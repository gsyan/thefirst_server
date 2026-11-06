package com.bk.sbs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PurchaseRewardCardRequest
 * Auto-generated from Unity C# PurchaseRewardCardRequest class
 */
@Data
@NoArgsConstructor
public class PurchaseRewardCardRequest {
    private Integer zoneNumber;
    private Integer cellRow;
    private Integer cellCol;
    private String cardId;
    private Integer mineralSpent;
}
