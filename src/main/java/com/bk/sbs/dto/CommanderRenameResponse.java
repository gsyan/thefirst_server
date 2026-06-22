package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CommanderRenameResponse
 * Auto-generated from Unity C# CommanderRenameResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CommanderRenameResponse {
    private String commanderName;
    private Integer nameChangeCount;
}
