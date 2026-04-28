package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ShipHealthInfoDto
 * Auto-generated from Unity C# ShipHealthInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ShipHealthInfoDto {
    private Long shipId;
    private List<BodyHealthEntryDto> bodies;
}
