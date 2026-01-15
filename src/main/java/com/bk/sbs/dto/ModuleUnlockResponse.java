package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleUnlockResponse
 * Auto-generated from Unity C# ModuleUnlockResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleUnlockResponse {
    private Long shipId;
    private Integer bodyIndex;
    private Integer moduleTypePacked;
    private Integer slotIndex;
    private CostRemainInfoDto costRemainInfo;
}
