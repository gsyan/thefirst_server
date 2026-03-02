package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneRankingResponse
 * Auto-generated from Unity C# ZoneRankingResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneRankingResponse {
    private Integer totalCount;
    private List<RankingEntryDto> items;
    private RankingEntryDto myInfo;
    private String lastUpdatedAt;
}
