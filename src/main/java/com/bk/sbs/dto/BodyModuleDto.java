package com.bk.sbs.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BodyModuleDto {
    private int moduleType;
    private int moduleLevel;
    private int bodyIndex;
    private LocalDateTime created;
    private List<EngineModuleDto> engines;
    private List<WeaponModuleDto> weapons;
    private List<HangerModuleDto> hangers;
}
