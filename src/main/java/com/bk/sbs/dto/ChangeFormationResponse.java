package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChangeFormationResponse
 * Auto-generated from Unity C# ChangeFormationResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ChangeFormationResponse {
    private Boolean success;
    private String message;
    private FleetInfoDto updatedFleetInfo;
}
