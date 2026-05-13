package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TechLevelResearchResponse
 * Auto-generated from Unity C# TechLevelResearchResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class TechLevelResearchResponse {
    private Integer techPointRemain;
    private List<String> researchedIds;
}
