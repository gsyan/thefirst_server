package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ServerStatusResponse
 * Auto-generated from Unity C# ServerStatusResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ServerStatusResponse {
    private Boolean updateRequired;
    private Integer minVersionCode;
    private String minVersionName;
    private Boolean working;
    private String endTime;
}
