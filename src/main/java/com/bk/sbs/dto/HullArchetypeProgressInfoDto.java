package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HullArchetypeProgressInfoDto
 * Auto-generated from Unity C# HullArchetypeProgressInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class HullArchetypeProgressInfoDto {
    private String hullArchetype;
    private String counterType;
    private Long counterValue;
}
