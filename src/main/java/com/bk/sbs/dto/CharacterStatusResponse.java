package com.bk.sbs.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CharacterStatusResponse {
    private String characterName;
    private Long money;
    private Long mineral;
    private Integer technologyLevel;
}