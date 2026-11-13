package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ArchetypeCounterReportDto
 * Auto-generated from Unity C# ArchetypeCounterReport class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ArchetypeCounterReportDto {
    private String hullArchetype;
    private String counterType;
    private Long value;
}
