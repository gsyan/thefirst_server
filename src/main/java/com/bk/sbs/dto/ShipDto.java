package com.bk.sbs.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ShipDto {
    private Long id;
    private Long fleetId;
    private String shipName;
    private int positionIndex;
    private String description;
    private LocalDateTime created;
    private LocalDateTime modified;
    private List<BodyModuleDto> bodies;
}
