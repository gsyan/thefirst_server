//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private FleetDto activeFleetInfo;
    private CharacterStatusResponse characterInfo;
    private List<ModuleResearchResponse.ResearchedModuleInfo> researchedModules; // 개발된 모듈 목록
}