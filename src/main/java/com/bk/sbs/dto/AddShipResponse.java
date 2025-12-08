package com.bk.sbs.dto;

public class AddShipResponse {
    private boolean success;
    private String message;
    private ShipDto newShipInfo;
    private CostInfo totalCost;
    private Long remainMoney;
    private Long remainMineral;
    private FleetDto updatedFleetInfo;

    // Default constructor
    public AddShipResponse() {}

    // Constructor with parameters
    public AddShipResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
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

    public ShipDto getNewShipInfo() {
        return newShipInfo;
    }

    public void setNewShipInfo(ShipDto newShipInfo) {
        this.newShipInfo = newShipInfo;
    }

    public CostInfo getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(CostInfo totalCost) {
        this.totalCost = totalCost;
    }

    public Long getRemainMoney() {
        return remainMoney;
    }

    public void setRemainMoney(Long remainMoney) {
        this.remainMoney = remainMoney;
    }

    public Long getRemainMineral() {
        return remainMineral;
    }

    public void setRemainMineral(Long remainMineral) {
        this.remainMineral = remainMineral;
    }

    public FleetDto getUpdatedFleetInfo() {
        return updatedFleetInfo;
    }

    public void setUpdatedFleetInfo(FleetDto updatedFleetInfo) {
        this.updatedFleetInfo = updatedFleetInfo;
    }
}