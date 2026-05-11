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
    private Integer mineral;
    private Integer techPoint;
    private Integer modulePoint;
    private Integer modulePointMaxGot;
    private Integer pvpPoint;
    private Integer pvpPointMaxGot;
    private String pvpPointExpiry;
    private List<String> clearedZones;
    private Integer nameChangeCount;
}
