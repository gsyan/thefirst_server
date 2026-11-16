//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto.nogenerated.admin;

import com.bk.sbs.dto.NoticeTextDto;
import lombok.Data;

import java.util.List;

// 어드민 전용 DTO — Unity 클라이언트가 호출하지 않으므로 제너레이터 대상 아님(dto/nogenerated 관례)
// id가 null이면 신규 생성, 있으면 수정(수정 시 서버에서 version 자동 증가)
@Data
public class NoticeSaveRequest {
    private Long id;
    private List<NoticeTextDto> texts; // 언어별 제목/본문
    private boolean enabled;
    private int displayOrder;
}
