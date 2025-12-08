package com.bk.sbs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WeaponModuleDto {
    private int moduleType;
    private int moduleLevel;
    private int bodyIndex;
    private int slotIndex;
    private LocalDateTime created;
}
