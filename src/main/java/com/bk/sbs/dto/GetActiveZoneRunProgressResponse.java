package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetActiveZoneRunProgressResponse
 * Auto-generated from Unity C# GetActiveZoneRunProgressResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class GetActiveZoneRunProgressResponse {
    private Integer zoneNumber;
    private List<String> clearedCells;
    private Integer explorationPointBanked;
    private Integer commanderExpBanked;
    private List<ShipHealthRatioInfoDto> shipHealthRatios;
    private List<String> selectedRewardCards;
    private List<String> pendingRewardCardCandidates;
}
