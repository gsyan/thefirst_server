package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PurchaseRewardCardResponse
 * Auto-generated from Unity C# PurchaseRewardCardResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PurchaseRewardCardResponse {
    private List<String> rewardCardCandidates;
    private Integer mineralAmount;
}
