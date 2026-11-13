package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ScavengeSettings
 * Auto-generated from Unity C# ScavengeSettings class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ScavengeSettings {
    private Integer baseChancePercent;

    private Integer mineralMin;

    private Integer mineralMax;

    private Integer mineralStep;
}
