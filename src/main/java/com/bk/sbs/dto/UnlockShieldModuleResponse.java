package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UnlockShieldModuleResponse
 * Auto-generated from Unity C# UnlockShieldModuleResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UnlockShieldModuleResponse {
    private String hullSubType;
    private Integer achievementPointRemain;
    private List<String> unlockedShieldHulls;
}
