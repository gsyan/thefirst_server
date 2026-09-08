package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UnlockHullResponse
 * Auto-generated from Unity C# UnlockHullResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UnlockHullResponse {
    private String hullSubType;
    private Integer achievementPointRemain;
    private List<String> unlockedHulls;
}
