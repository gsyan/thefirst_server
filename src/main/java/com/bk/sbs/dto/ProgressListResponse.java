package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ProgressListResponse
 * Auto-generated from Unity C# ProgressListResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ProgressListResponse {
    private List<ProgressInfoDto> progressList;
}
