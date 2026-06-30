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
    private Integer mineral;
    private Integer commanderLevel;
    private Integer exp;
    private Integer modulePoint;
    private Integer modulePointMaxGot;
    private Integer pvpPoint;
    private Integer pvpPointMaxGot;
    private String pvpPointExpiry;
    private List<String> clearedZones;
    private Integer nameChangeCount;
}
