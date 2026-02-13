package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpBattleResultResponse
 * Auto-generated from Unity C# PvpBattleResultResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpBattleResultResponse {
    private Integer scoreChange;
    private Integer newScore;
    private Integer newRank;
}
