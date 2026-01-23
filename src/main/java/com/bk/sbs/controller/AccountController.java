//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
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
        String message = accountService.signUp(request);
        return ApiResponse.success(message);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = accountService.login(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = accountService.refreshToken(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/google-login")
    public ApiResponse<AuthResponse> googleLogin(@RequestBody GoogleLoginRequest request) {
        AuthResponse response = accountService.googleLogin(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/guest-login")
    public ApiResponse<AuthResponse> guestLogin(@RequestBody GuestLoginRequest request) {
        AuthResponse response = accountService.guestLogin(request);
        return ApiResponse.success(response);
    }

}
