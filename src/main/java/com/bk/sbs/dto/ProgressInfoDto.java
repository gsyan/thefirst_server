package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ProgressInfoDto
 * Auto-generated from Unity C# ProgressInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ProgressInfoDto {
    private String category;
    private String key;
    private String completedDateTime;
}
