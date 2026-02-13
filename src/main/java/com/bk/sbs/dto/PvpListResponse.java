package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpListResponse
 * Auto-generated from Unity C# PvpListResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpListResponse {
    private List<PvpOpponentInfoDto> opponents;
    private PvpRankInfoDto myRankInfo;
}
