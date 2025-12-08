//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CharacterResponse {
    private Long id;
    private Long characterId; // worldId(8비트) + id(54비트)
    private String characterName;
    private String dateTime;

    public CharacterResponse(Long id, String characterName, String dateTime, int worldId) {
        this.id = id;
        this.characterId = ((long) worldId << 56) | id; // 동적으로 characterId 계산
        this.characterName = characterName;
        this.dateTime = dateTime;
    }
}