package com.bk.sbs.exception;

public class BusinessException extends RuntimeException {
    private final ServerErrorCode errorCode;

    public BusinessException(ServerErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ServerErrorCode getErrorCode() {
        return errorCode;
    }
}