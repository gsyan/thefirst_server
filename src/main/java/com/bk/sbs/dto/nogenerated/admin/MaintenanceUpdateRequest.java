//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto.nogenerated.admin;

import lombok.Data;

// 어드민 전용 DTO — Unity 클라이언트가 호출하지 않으므로 제너레이터 대상 아님(dto/nogenerated 관례)
@Data
public class MaintenanceUpdateRequest {
    private boolean working;
    private String endTime;  // ISO-8601 (예: 2026-07-03T15:00:00Z), 비어있으면 미정
}
