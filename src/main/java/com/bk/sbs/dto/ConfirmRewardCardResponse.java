package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ConfirmRewardCardResponse
 * Auto-generated from Unity C# ConfirmRewardCardResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ConfirmRewardCardResponse {
    private String selectedCardId;
    private Integer explorationPointGained;
}
