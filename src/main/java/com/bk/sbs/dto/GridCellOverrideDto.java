package com.bk.sbs.dto;

import com.bk.sbs.enums.EGridCellType;
import com.bk.sbs.enums.EGridEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GridCellOverrideDto
 * Auto-generated from Unity C# GridCellOverride class (nested type referenced by ZoneConfig)
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class GridCellOverrideDto {
    private Integer row;
    private Integer col;
    private EGridCellType type;
    private EGridEventType eventType;
}
