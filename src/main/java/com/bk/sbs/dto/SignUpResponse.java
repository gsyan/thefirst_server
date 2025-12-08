//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignUpResponse {
    private String message;

    public SignUpResponse(String message) {
        this.message = message;
    }
}