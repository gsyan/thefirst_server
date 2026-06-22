package com.bk.sbs.controller;

import com.bk.sbs.dto.DailyClaimResponse;
import com.bk.sbs.dto.VipPurchaseRequest;
import com.bk.sbs.dto.VipStatusResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.IapService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iap")
public class IapController {

    private final IapService iapService;
    private final JwtUtil jwtUtil;

    public IapController(IapService iapService, JwtUtil jwtUtil) {
        this.iapService = iapService;
        this.jwtUtil = jwtUtil;
    }

    // VIP 구매 영수증 검증 + VIP 만료일 저장
    @PostMapping("/vip/purchase")
    public ResponseEntity<ApiResponse<VipStatusResponse>> purchaseVip(
            @RequestBody VipPurchaseRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        VipStatusResponse response = iapService.purchaseVip(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 현재 VIP 상태 조회
    @GetMapping("/vip/status")
    public ResponseEntity<ApiResponse<VipStatusResponse>> getVipStatus(HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        VipStatusResponse response = iapService.getVipStatus(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 에디터 전용: 영수증 없이 VIP 강제 세팅
    @PostMapping("/debug/vip/force")
    public ResponseEntity<ApiResponse<VipStatusResponse>> debugForceVip(HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        VipStatusResponse response = iapService.debugForceVip(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 일일 보상 지급 요청
    @PostMapping("/vip/daily-reward")
    public ResponseEntity<ApiResponse<DailyClaimResponse>> claimDailyReward(HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        DailyClaimResponse response = iapService.claimDailyReward(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long getCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.IAP_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCommanderId(token) == false) throw new BusinessException(ServerErrorCode.IAP_CONTROLLER_FAIL_JWT_HAS_COMMANDERID);
        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.IAP_CONTROLLER_FAIL_JWT_GET_COMMANDERID);
        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}



