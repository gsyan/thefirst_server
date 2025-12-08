package com.bk.sbs.dto;

public class ResourceUpdateRequest {
    private Long money;
    private Long mineral;

    public Long getMoney() {
        return money;
    }

    public void setMoney(Long money) {
        this.money = money;
    }

    public Long getMineral() {
        return mineral;
    }

    public void setMineral(Long mineral) {
        this.mineral = mineral;
    }
}