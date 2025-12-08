package com.bk.sbs.dto;

import com.bk.sbs.enums.EFormationType;

public class ChangeFormationRequest {
    private Long fleetId;
    private EFormationType formationType;

    // Default constructor
    public ChangeFormationRequest() {}

    // Constructor with parameters
    public ChangeFormationRequest(Long fleetId, EFormationType formationType) {
        this.fleetId = fleetId;
        this.formationType = formationType;
    }

    // Getters and setters
    public Long getFleetId() {
        return fleetId;
    }

    public void setFleetId(Long fleetId) {
        this.fleetId = fleetId;
    }

    public EFormationType getFormationType() {
        return formationType;
    }

    public void setFormationType(EFormationType formationType) {
        this.formationType = formationType;
    }
}