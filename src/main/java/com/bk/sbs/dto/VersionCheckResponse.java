package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VersionCheckResponse
 * Auto-generated from Unity C# VersionCheckResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class VersionCheckResponse {
    private Boolean updateRequired;
    private Integer minVersionCode;
    private String minVersionName;
}
