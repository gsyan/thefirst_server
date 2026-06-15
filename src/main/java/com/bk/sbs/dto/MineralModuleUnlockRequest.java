package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MineralModuleUnlockRequest
 * Auto-generated from Unity C# MineralModuleUnlockRequest class
 */
@Data
@NoArgsConstructor
public class MineralModuleUnlockRequest {
    private Long shipId;
    private Integer bodyIndex;
    private EModuleType moduleType;
    private Integer slotIndex;
}
