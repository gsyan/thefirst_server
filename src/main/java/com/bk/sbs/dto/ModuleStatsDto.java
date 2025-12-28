package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModuleStatsDto
 * Auto-generated from Unity C# ModuleStats class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ModuleStatsDto {
    private Float health;
    private Float attackPower;
    private Float movementSpeed;
    private Float rotationSpeed;
    private Float cargoCapacity;
}
