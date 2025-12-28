package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PartsBodyInfoDto
 * Auto-generated from Unity C# PartsBodyInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PartsBodyInfoDto {
    private Integer bodyIndex;
    private Integer level;
    private ModuleStatsDto stats;
    private List<ModuleInfoDto> weapons;
    private List<ModuleInfoDto> engines;
}
