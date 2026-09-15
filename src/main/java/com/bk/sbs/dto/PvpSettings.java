package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpSettings
 * Auto-generated from Unity C# PvpSettings class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpSettings {
    private Integer pvpMinCommanderLevel;

    private Integer pvpListCount;

    private Integer pvpListRefreshCount;

    private Integer pvpRankScoreInit;

    private Integer pvpRankScorePenalty;
}
