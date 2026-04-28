package com.bk.sbs.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FleetHealthSaveRequest
 * Auto-generated from Unity C# FleetHealthSaveRequest class
 */
@Data
@NoArgsConstructor
public class FleetHealthSaveRequest {
    private List<ShipHealthInfoDto> ships;
}
