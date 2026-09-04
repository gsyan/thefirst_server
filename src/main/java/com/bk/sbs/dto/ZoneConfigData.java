package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneConfigData
 * Auto-generated from Unity C# ZoneConfig class (server-required fields only)
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneConfigData {
    private Integer zoneIndex;
    private Integer gridWidth;
    private Integer gridHeight;
    private List<GridCellOverrideDto> cellOverrides;
    private Integer explorationPointReward;
    private Integer commanderExpReward;
}
