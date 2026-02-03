package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneClearResponse
 * Auto-generated from Unity C# ZoneClearResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneClearResponse {
    private String clearedZone;  // 업데이트된 최고 클리어 zone
    private CostRemainInfoDto rewardInfo;  // 클리어 보상 (광물 등)
}
