package com.bk.sbs.dto;

public class ModuleChangeRequest {
    private long shipId;
    private int bodyIndex;
    private int slotIndex;
    private String currentModuleType;
    private String newModuleType;
    private int newModuleLevel;

    public long getShipId() {
        return shipId;
    }

    public void setShipId(long shipId) {
        this.shipId = shipId;
    }

    public int getBodyIndex() {
        return bodyIndex;
    }

    public void setBodyIndex(int bodyIndex) {
        this.bodyIndex = bodyIndex;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public String getCurrentModuleType() {
        return currentModuleType;
    }

    public void setCurrentModuleType(String currentModuleType) {
        this.currentModuleType = currentModuleType;
    }

    public String getNewModuleType() {
        return newModuleType;
    }

    public void setNewModuleType(String newModuleType) {
        this.newModuleType = newModuleType;
    }

    public int getNewModuleLevel() {
        return newModuleLevel;
    }

    public void setNewModuleLevel(int newModuleLevel) {
        this.newModuleLevel = newModuleLevel;
    }
}
