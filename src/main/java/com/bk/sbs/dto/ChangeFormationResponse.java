package com.bk.sbs.dto;

public class ChangeFormationResponse {
    private boolean success;
    private String message;
    private FleetDto updatedFleetInfo;

    // Default constructor
    public ChangeFormationResponse() {}

    // Constructor with parameters
    public ChangeFormationResponse(boolean success, String message, FleetDto updatedFleetInfo) {
        this.success = success;
        this.message = message;
        this.updatedFleetInfo = updatedFleetInfo;
    }

    // Static factory methods
    public static ChangeFormationResponse success(FleetDto fleet) {
        return new ChangeFormationResponse(true, "Formation changed successfully", fleet);
    }

    public static ChangeFormationResponse failure(String message) {
        return new ChangeFormationResponse(false, message, null);
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public FleetDto getUpdatedFleetInfo() {
        return updatedFleetInfo;
    }

    public void setUpdatedFleetInfo(FleetDto updatedFleetInfo) {
        this.updatedFleetInfo = updatedFleetInfo;
    }
}