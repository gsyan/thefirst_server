package com.bk.sbs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleChangeRequest
 * Auto-generated from Unity C# ModuleChangeRequest class
 */
@Data
@NoArgsConstructor
public class ModuleChangeRequest {
    private Long shipId;
    private Integer bodyIndex;
    private Integer currentModuleTypePacked;
    private Integer newModuleTypePacked;
    private Integer slotIndex;
}
