package com.bk.sbs.controller;

import com.bk.sbs.dto.RedeemCodeRequest;
import com.bk.sbs.dto.RedeemCodeResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.RedeemCodeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/redeem-code")
public class RedeemCodeController {

    private final RedeemCodeService redeemCodeService;
    private final JwtUtil jwtUtil;

    public RedeemCodeController(RedeemCodeService redeemCodeService, JwtUtil jwtUtil) {
        this.redeemCodeService = redeemCodeService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ApiResponse<RedeemCodeResponse> redeemCode(@RequestBody RedeemCodeRequest request, HttpServletRequest httpRequest) {
        Long actualCommanderId = getActualCommanderIdFromToken(httpRequest);
        RedeemCodeResponse response = redeemCodeService.redeem(actualCommanderId, request.getCode());
        return ApiResponse.success(response);
    }

    // JWT 토큰에서 커맨더 ID 추출 (비트 마스킹 포함)
    private Long getActualCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.REDEEM_CODE_FAIL_NULL_TOKEN);
        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.REDEEM_CODE_FAIL_INVALID_COMMANDER);
        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}
