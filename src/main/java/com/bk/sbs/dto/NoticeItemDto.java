package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NoticeItemDto
 * Auto-generated from Unity C# NoticeItem class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class NoticeItemDto {
    private Long noticeId;
    private Integer version;
    private String title;
    private String body;
}
