package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneCollectResponse
 * Auto-generated from Unity C# ZoneCollectResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneCollectResponse {
    private String collectDateTime;
    private Long onlineSeconds;      // 온라인 미수집 구간(초)
    private Long offlineSeconds;     // 오프라인 적립 구간(초, 캡 적용)
    private Long offlineCapSeconds;  // 적용된 오프라인 캡(초)
    private CostRemainInfoDto onlineRewardInfo;  // 온라인 구간 보상 (mineralCost 필드 사용)
    private CostRemainInfoDto rewardInfo;        // 전체 보상 (remainMineral = 최종 잔액)
}
