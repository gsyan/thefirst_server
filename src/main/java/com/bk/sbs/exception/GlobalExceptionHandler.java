//--------------------------------------------------------------------------------------------------
package com.bk.sbs.exception;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 명시적으로 호출되지 않았으나, 스프링의 @RestControllerAdvice로 자동 적용됨
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ResponseEntity<>(ApiResponse.error(determineErrorCode(ex.getMessage())), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneralException(Exception ex) {
        return new ResponseEntity<>(ApiResponse.error(ServerErrorCode.SUCCESS), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // determineErrorCode 메서드로 예외 메시지를 분석하여 적절한 ServerErrorCode를 반환. 향후 더 정교한 매핑 필요.
    private ServerErrorCode determineErrorCode(String message) {
        if (message.contains("Email already exists")) {
            return ServerErrorCode.ACCOUNT_REGISTER_FAIL_REASON1;
        } else if (message.contains("Invalid email or password")) {
            return ServerErrorCode.LOGIN_FAIL_REASON1;
        } else if (message.contains("Character name already exists")) {
            return ServerErrorCode.CHARACTER_CREATE_FAIL_REASON1;
        }
        return ServerErrorCode.SUCCESS; // 기본값
    }

}