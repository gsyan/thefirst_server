package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RankingEntryDto
 * Auto-generated from Unity C# RankingEntry class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class RankingEntryDto {
    private Integer rank;
    private Long commanderId;
    private String commanderName;
    private String score;
    private Boolean isPerfectClear;
}
