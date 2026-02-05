package com.bk.sbs.dto;

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
    private String characterName;
    private Integer techLevel;
    private Long mineral;
    private Long mineralRare;
    private Long mineralExotic;
    private Long mineralDark;
    private String clearedZone;
    private String collectDateTime;
}
