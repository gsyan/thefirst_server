package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetRewardCardShopResponse
 * Auto-generated from Unity C# GetRewardCardShopResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class GetRewardCardShopResponse {
    private List<String> rewardCardCandidates;
    private Integer rerollRemain;
}
