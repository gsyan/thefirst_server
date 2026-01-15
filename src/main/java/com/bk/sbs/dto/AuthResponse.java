package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthResponse
 * Auto-generated from Unity C# AuthResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private FleetInfoDto activeFleetInfo;
    private CharacterInfoDto characterInfo;
    private List<List<Integer>> researchedModuleTypes;
}
