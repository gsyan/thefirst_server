package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UnlockInterceptorModuleResponse
 * Auto-generated from Unity C# UnlockInterceptorModuleResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UnlockInterceptorModuleResponse {
    private String hullSubType;
    private Integer achievementPointRemain;
    private List<String> unlockedInterceptorHulls;
}
