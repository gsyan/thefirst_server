//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto.nogenerated.admin;

import lombok.Data;

// 어드민 전용 DTO — Unity 클라이언트가 호출하지 않으므로 제너레이터 대상 아님(dto/nogenerated 관례)
@Data
public class NoticeAdminDto {
    private Long id;
    private String title;
    private String body;
    private boolean enabled;
    private int displayOrder;
    private int version;
}
