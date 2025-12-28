package com.bk.sbs.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleUpgradeRequest
 * Auto-generated from Unity C# ModuleUpgradeRequest class
 */
@Data
@NoArgsConstructor
public class ModuleUpgradeRequest {
    private Long shipId;
    private Integer bodyIndex;
    private Integer moduleTypePacked;
    private Integer slotIndex;
    private Integer currentLevel;
    private Integer targetLevel;
}
