package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpgradeArchetypeModuleRequest
 * Auto-generated from Unity C# UpgradeArchetypeModuleRequest class
 */
@Data
@NoArgsConstructor
public class UpgradeArchetypeModuleRequest {
    private String hullArchetype;
    private EModuleType moduleType;
    private Integer slotIndex;
}
