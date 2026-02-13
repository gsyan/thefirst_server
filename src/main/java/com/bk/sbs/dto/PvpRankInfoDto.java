package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpRankInfoDto
 * Auto-generated from Unity C# PvpRankInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpRankInfoDto {
    private Integer pvpScore;
    private Integer pvpWins;
    private Integer pvpLosses;
    private Integer pvpRank;
    private Integer pvpListRefreshRemain;
}
