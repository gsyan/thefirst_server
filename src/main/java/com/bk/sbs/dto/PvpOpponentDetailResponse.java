package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpOpponentDetailResponse
 * Auto-generated from Unity C# PvpOpponentDetailResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpOpponentDetailResponse {
    private Long commanderId;
    private String commanderName;
    private Integer commanderLevel;
    private Integer pvpScore;
    private Integer rank;
    private Integer wins;
    private Integer losses;
    private FleetInfoDto fleetInfo;
    private List<HullArchetypeProgressInfoDto> passiveProgress;
    private List<ArchetypeUpgradeInfoDto> archetypeUpgrades;
}
