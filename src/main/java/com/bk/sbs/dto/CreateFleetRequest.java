package com.bk.sbs.dto;

import lombok.Data;

@Data
public class CreateFleetRequest {
    private String fleetName;
    private String description;
}
