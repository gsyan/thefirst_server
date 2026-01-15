package com.bk.sbs.dto;

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
    private Integer moduleTypePacked;
    private Integer slotIndex;
}
