package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CostStructDto
 * Auto-generated from Unity C# CostStruct class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CostStructDto {
    private Integer techLevel;
    private Long mineral;
    private Long mineralRare;
    private Long mineralExotic;
    private Long mineralDark;
}
