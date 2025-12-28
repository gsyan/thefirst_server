package com.bk.sbs.dto;

import com.bk.sbs.enums.EFormationType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChangeFormationRequest
 * Auto-generated from Unity C# ChangeFormationRequest class
 */
@Data
@NoArgsConstructor
public class ChangeFormationRequest {
    private Long fleetId;
    private EFormationType formationType;
}
