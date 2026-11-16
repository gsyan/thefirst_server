//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto.nogenerated.admin;

import com.bk.sbs.dto.NoticeTextDto;
import lombok.Data;

import java.util.List;

// 어드민 전용 DTO — Unity 클라이언트가 호출하지 않으므로 제너레이터 대상 아님(dto/nogenerated 관례)
@Data
public class NoticeAdminDto {
    private Long id;
    private List<NoticeTextDto> texts; // 언어별 제목/본문
    private boolean enabled;
    private int displayOrder;
    private int version;
}
