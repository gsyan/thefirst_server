package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleUnlockRequest
 * Auto-generated from Unity C# ModuleUnlockRequest class
 */
@Data
@NoArgsConstructor
public class ModuleUnlockRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private Integer slotIndex;
}
