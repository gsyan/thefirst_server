package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneClearRequest
 * Auto-generated from Unity C# ZoneClearRequest class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneClearRequest {
    private String zoneName;  // 클리어한 zone 이름 (예: "2-5")
}
