package com.bk.sbs.dto;

public class ModuleUnlockRequest {
    private long shipId;
    private int bodyIndex;
    private int moduleType;
    private int moduleSubTypeValue;
    private int slotIndex;

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

    public int getModuleType() {
        return moduleType;
    }

    public void setModuleType(int moduleType) {
        this.moduleType = moduleType;
    }

    public int getModuleSubTypeValue() {
        return moduleSubTypeValue;
    }

    public void setModuleSubTypeValue(int moduleSubTypeValue) {
        this.moduleSubTypeValue = moduleSubTypeValue;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }
}
