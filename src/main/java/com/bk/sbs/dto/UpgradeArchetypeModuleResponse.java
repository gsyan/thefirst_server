package com.bk.sbs.dto;

import com.bk.sbs.enums.EModuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpgradeArchetypeModuleResponse
 * Auto-generated from Unity C# UpgradeArchetypeModuleResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UpgradeArchetypeModuleResponse {
    private String hullArchetype;
    private EModuleType moduleType;
    private Integer slotIndex;
    private Integer level;
    private Integer achievementPointRemain;
}
