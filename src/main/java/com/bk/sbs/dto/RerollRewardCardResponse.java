package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RerollRewardCardResponse
 * Auto-generated from Unity C# RerollRewardCardResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class RerollRewardCardResponse {
    private List<String> rewardCardCandidates;
    private Integer rerollRemain;
}
