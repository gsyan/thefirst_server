package com.bk.sbs.dto;

public class CostInfo {
    private int moneyCost;
    private int mineralCost;
    private int remainMoney;
    private int remainMineral;

    public CostInfo() {}

    public CostInfo(int moneyCost, int mineralCost, int remainMoney, int remainMineral) {
        this.moneyCost = moneyCost;
        this.mineralCost = mineralCost;
        this.remainMoney = remainMoney;
        this.remainMineral = remainMineral;
    }

    public int getMoneyCost() {
        return moneyCost;
    }

    public void setMoneyCost(int moneyCost) {
        this.moneyCost = moneyCost;
    }

    public int getMineralCost() {
        return mineralCost;
    }

    public void setMineralCost(int mineralCost) {
        this.mineralCost = mineralCost;
    }

    public int getRemainMoney() {
        return remainMoney;
    }

    public void setRemainMoney(int remainMoney) {
        this.remainMoney = remainMoney;
    }

    public int getRemainMineral() {
        return remainMineral;
    }

    public void setRemainMineral(int remainMineral) {
        this.remainMineral = remainMineral;
    }
}