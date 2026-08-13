package com.bk.sbs.controller;

import com.bk.sbs.dto.DailyClaimResponse;
import com.bk.sbs.dto.VipPurchaseRequest;
import com.bk.sbs.dto.VipStatusResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.IapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iap")
public class IapController {

    private final IapService iapService;

    public IapController(IapService iapService) {
        this.iapService = iapService;
    }

    // VIP 구매 영수증 검증 + VIP 만료일 저장
    @PostMapping("/vip/purchase")
    public ResponseEntity<ApiResponse<VipStatusResponse>> purchaseVip(
            @RequestBody VipPurchaseRequest request,
            @CommanderId Long commanderId) {
        VipStatusResponse response = iapService.purchaseVip(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 현재 VIP 상태 조회
    @GetMapping("/vip/status")
    public ResponseEntity<ApiResponse<VipStatusResponse>> getVipStatus(@CommanderId Long commanderId) {
        VipStatusResponse response = iapService.getVipStatus(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 에디터 전용: 영수증 없이 VIP 강제 세팅
    @PostMapping("/debug/vip/force")
    public ResponseEntity<ApiResponse<VipStatusResponse>> debugForceVip(@CommanderId Long commanderId) {
        VipStatusResponse response = iapService.debugForceVip(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 일일 보상 지급 요청
    @PostMapping("/vip/daily-reward")
    public ResponseEntity<ApiResponse<DailyClaimResponse>> claimDailyReward(@CommanderId Long commanderId) {
        DailyClaimResponse response = iapService.claimDailyReward(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}



