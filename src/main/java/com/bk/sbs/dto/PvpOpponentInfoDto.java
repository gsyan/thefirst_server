package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpOpponentInfoDto
 * Auto-generated from Unity C# PvpOpponentInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpOpponentInfoDto {
    private Long commanderId;
    private String commanderName;
    private Integer pvpScore;
    private Integer rank;
    private FleetInfoDto fleetInfo;
}
