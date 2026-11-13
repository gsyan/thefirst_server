package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ScoutSettings
 * Auto-generated from Unity C# ScoutSettings class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ScoutSettings {
    private Float intervalSeconds;

    private Integer mineralMin;

    private Integer mineralMax;

    private Integer mineralStep;
}
