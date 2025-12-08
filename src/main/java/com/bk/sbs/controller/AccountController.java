//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.AccountService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final JwtUtil jwtUtil;

    public AccountController(AccountService accountService, JwtUtil jwtUtil) {
        this.accountService = accountService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/signup")
    public ApiResponse<String> signUp(@RequestBody SignUpRequest request) {
        try {
            String message = accountService.signUp(request);
            return ApiResponse.success(message);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getErrorCode());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ServerErrorCode.ACCOUNT_REGISTER_FAIL_REASON1);
        } catch (Exception e) {
            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request)
    {
        try {
            AuthResponse response = accountService.login(request);
            return ApiResponse.success(response);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getErrorCode());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ServerErrorCode.LOGIN_FAIL_REASON1);
        } catch (Exception e) {
            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = accountService.refreshToken(request);
            return ApiResponse.success(response);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getErrorCode());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ServerErrorCode.LOGIN_FAIL_REASON1);
        } catch (Exception e) {
            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
        }
    }

    @PostMapping("/google-login")
    public ApiResponse<AuthResponse> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            AuthResponse response = accountService.googleLogin(request);
            return ApiResponse.success(response);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getErrorCode());
        } catch (Exception e) {
            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
        }
    }

}
