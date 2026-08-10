package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CommanderInfoDto
 * Auto-generated from Unity C# CommanderInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CommanderInfoDto {
    private Long commanderId;
    private String commanderName;
    private Integer nameChangeCount;
    private Integer commanderLevel;
    private Integer exp;
    private Integer commandPowerMax;
    private Integer explorationSeedBase;
    private List<String> clearedZones;
    private Integer explorationPoint;
    private Integer explorationZoneNumber;
    private String explorationCell;
    private Integer highestClearedZoneNumber;
    private Integer pvpPoint;
    private Integer pvpPointMaxGot;
    private String pvpPointExpiry;
}
