package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ShipInfoDto
 * Auto-generated from Unity C# ShipInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ShipInfoDto {
    private Long id;
    private Long fleetId;
    private String shipName;
    private Integer positionIndex;
    private String description;
    private List<ModuleBodyInfoDto> bodies;
}
