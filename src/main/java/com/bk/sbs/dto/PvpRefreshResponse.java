package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PvpRefreshResponse
 * Auto-generated from Unity C# PvpRefreshResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PvpRefreshResponse {
    private List<PvpOpponentInfoDto> opponents;
    private Integer refreshRemain;
}
