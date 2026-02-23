package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpRankingEntryDto
 * Auto-generated from Unity C# PvpRankingEntry class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpRankingEntryDto {
    private Integer rank;
    private Long characterId;
    private String characterName;
    private Integer pvpScore;
}
