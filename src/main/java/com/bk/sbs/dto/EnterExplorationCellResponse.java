package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EnterExplorationCellResponse
 * Auto-generated from Unity C# EnterExplorationCellResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EnterExplorationCellResponse {
    private Integer zoneNumber;
    private Integer cellRow;
    private Integer cellCol;
    private String challengeToken;
}
