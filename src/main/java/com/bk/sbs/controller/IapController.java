package com.bk.sbs.controller;

import com.bk.sbs.dto.VipDailyMineralResponse;
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
        Long characterId = getCharacterIdFromToken(httpRequest);
        VipStatusResponse response = iapService.purchaseVip(characterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 현재 VIP 상태 조회
    @GetMapping("/vip/status")
    public ResponseEntity<ApiResponse<VipStatusResponse>> getVipStatus(HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        VipStatusResponse response = iapService.getVipStatus(characterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // VIP 일일 미네랄 지급 요청
    @PostMapping("/vip/daily-mineral")
    public ResponseEntity<ApiResponse<VipDailyMineralResponse>> claimDailyMineral(HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        VipDailyMineralResponse response = iapService.claimDailyMineral(characterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long getCharacterIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.IAP_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCharacterId(token) == false) throw new BusinessException(ServerErrorCode.IAP_CONTROLLER_FAIL_JWT_HAS_CHARACTERID);
        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) throw new BusinessException(ServerErrorCode.IAP_CONTROLLER_FAIL_JWT_GET_CHARACTERID);
        return characterId & 0x00FFFFFFFFFFFFFFL;
    }
}
