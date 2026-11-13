package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpBattleStartResponse
 * Auto-generated from Unity C# PvpBattleStartResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpBattleStartResponse {
    private FleetInfoDto opponentFleetInfo;
    private String battleToken;
    private List<HullArchetypeProgressInfoDto> opponentPassiveProgress;
    private List<ArchetypeUpgradeInfoDto> opponentArchetypeUpgrades;
}
