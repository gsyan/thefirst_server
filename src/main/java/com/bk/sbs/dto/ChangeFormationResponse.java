package com.bk.sbs.dto;

import com.bk.sbs.enums.EFormationType;
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
    private EFormationType formation;
}
