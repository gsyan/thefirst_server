package com.bk.sbs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ConfirmRewardCardRequest
 * Auto-generated from Unity C# ConfirmRewardCardRequest class
 */
@Data
@NoArgsConstructor
public class ConfirmRewardCardRequest {
    private Integer zoneNumber;
    private Integer cellRow;
    private Integer cellCol;
    private String selectedCardId;
}
