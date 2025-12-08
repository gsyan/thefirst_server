//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private FleetDto activeFleetInfo;
    private CharacterStatusResponse characterInfo;
}