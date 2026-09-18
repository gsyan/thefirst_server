//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto.nogenerated.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 어드민 전용 DTO — Unity 클라이언트가 호출하지 않으므로 제너레이터 대상 아님(dto/nogenerated 관례)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceStatusDto {
    private boolean working;
    private String endTime;
}
