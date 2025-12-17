package com.bk.sbs.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CharacterStatusResponse {
    private String characterName;
    private Integer techLevel;
    private Long mineral;
    private Long mineralRare;
    private Long mineralExotic;
    private Long mineralDark;
}