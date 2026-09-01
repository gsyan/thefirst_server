package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EscapeExplorationZoneResponse
 * Auto-generated from Unity C# EscapeExplorationZoneResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EscapeExplorationZoneResponse {
    private Integer explorationPointGained;
    private Integer explorationPointRemain;
    private Integer expGained;
    private Integer totalExp;
    private Integer commanderLevel;
    private Integer highestClearedZoneNumber;
    private Integer tacticPower;
}
