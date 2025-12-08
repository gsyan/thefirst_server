package com.bk.sbs.dto;

public class AddShipRequest {
    private Long fleetId; // 함선을 추가할 함대 ID (null이면 현재 활성 함대)

    // Default constructor
    public AddShipRequest() {}

    // Constructor with parameters
    public AddShipRequest(Long fleetId) {
        this.fleetId = fleetId;
    }

    // Getters and setters
    public Long getFleetId() {
        return fleetId;
    }

    public void setFleetId(Long fleetId) {
        this.fleetId = fleetId;
    }
}