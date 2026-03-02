package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CharacterRenameResponse
 * Auto-generated from Unity C# CharacterRenameResponse class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CharacterRenameResponse {
    private String characterName;
    private Integer nameChangeCount;
}
