package com.bk.sbs.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CharacterInfoDto
 * Auto-generated from Unity C# CharacterInfo class
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CharacterInfoDto {
    private Long characterId;
    private String characterName;
    private Long mineral;
    private Long mineralRare;
    private Long mineralExotic;
    private Long mineralDark;
    private List<String> clearedZones;
    private String collectDateTime;
    private Integer nameChangeCount;
}
