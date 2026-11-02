package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NoticeResponse
 * Auto-generated from Unity C# NoticeResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class NoticeResponse {
    private List<NoticeItemDto> notices;
}
