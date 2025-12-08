package com.bk.sbs.exception;

public enum ServerErrorCode {
    SUCCESS(0, "Success"),
    ACCOUNT_REGISTER_FAIL_REASON1(1001, "Account registration failed due to duplicate email"),
    ACCOUNT_NOT_FOUND(1002, "Account not found"),
    LOGIN_FAIL_REASON1(2001, "Invalid email or password"),
    INVALID_TOKEN(2002, "Invalid token"),
    CHARACTER_CREATE_FAIL_REASON1(3001, "Character creation failed"),
    CHARACTER_NAME_DUPLICATE(3002, "Character name already exists"),
    CHARACTER_SELECTION_REQUIRED(3003, "Character selection required"),
    CHARACTER_NOT_FOUND(3004, "Character not found"),
    FLEET_NOT_FOUND(4001, "Fleet not found"),
    FLEET_DUPLICATE_NAME(4002, "Fleet name already exists"),
    FLEET_ACCESS_DENIED(4003, "Fleet access denied"),
    SHIP_NOT_FOUND(4004, "Ship not found"),
    MODULE_NOT_FOUND(4005, "Module not found"),
    MODULE_LEVEL_MISMATCH(4006, "Module level mismatch"),
    MODULE_DATA_NOT_FOUND(4007, "Module data not found"),
    INSUFFICIENT_MONEY(4008, "Insufficient money for upgrade"),
    INSUFFICIENT_MINERAL(4009, "Insufficient mineral for upgrade"),
    ACTIVE_FLEET_NOT_FOUND(4010, "Active fleet not found"),
    FLEET_MAX_SHIPS_REACHED(4011, "Maximum ships per fleet reached"),
    INVALID_DATA_TABLE(5001, "Invalid data table provided"),
    UNKNOWN_ERROR(Integer.MAX_VALUE, "Unknown error");

    private final int code;
    private final String message;

    ServerErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
