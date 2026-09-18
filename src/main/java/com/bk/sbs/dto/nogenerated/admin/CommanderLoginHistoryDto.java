//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto.nogenerated.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

// 어드민 전용 DTO — Unity 클라이언트가 호출하지 않으므로 제너레이터 대상 아님(dto/nogenerated 관례)
@Data
@AllArgsConstructor
public class CommanderLoginHistoryDto {
    private Instant loginAt;
    private String loginType;
}
