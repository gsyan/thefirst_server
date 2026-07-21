package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TempFleetInfoDto
 * Auto-generated from Unity C# TempFleetInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class TempFleetInfoDto {
    private List<ExplorationShipSlotDto> ships;
}
