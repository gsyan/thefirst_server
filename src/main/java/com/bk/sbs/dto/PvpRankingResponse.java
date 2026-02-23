package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpRankingResponse
 * Auto-generated from Unity C# PvpRankingResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpRankingResponse {
    private Integer totalCount;
    private List<PvpRankingEntryDto> items;
}
