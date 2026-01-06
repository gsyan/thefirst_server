package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleChangeResponse
 * Auto-generated from Unity C# ModuleChangeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleChangeResponse {
    private Long shipId;
    private Integer bodyIndex;
    private Integer oldModuleTypePacked;
    private Integer newModuleTypePacked;
    private Integer slotIndex;
}
