package com.bk.sbs.dto;

import com.bk.sbs.enums.EFormationType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FleetInfoDto
 * Auto-generated from Unity C# FleetInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class FleetInfoDto {
    private Long id;
    private Long characterId;
    private String fleetName;
    private String description;
    private Boolean isActive;
    private EFormationType formation;
    private List<ShipInfoDto> ships;
}
