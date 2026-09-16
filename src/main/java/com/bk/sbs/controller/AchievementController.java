package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.AchievementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/achievement")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    // 업적 전체 목록 + 커맨더별 현재 진행도/수령 상태
    @PostMapping("/list")
    public ResponseEntity<ApiResponse<GetAchievementListResponse>> getAchievementList(
            @RequestBody GetAchievementListRequest request,
            @CommanderId Long actualCommanderId) {
        GetAchievementListResponse response = achievementService.getAchievementStatusList(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 완료된 업적 수령(업적포인트 지급)
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<ClaimAchievementResponse>> claimAchievement(
            @RequestBody ClaimAchievementRequest request,
            @CommanderId Long actualCommanderId) {
        boolean claimVip = request.getClaimVip() != null && request.getClaimVip();
        ClaimAchievementResponse response = achievementService.claimAchievement(actualCommanderId, request.getAchievementId(), claimVip);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 완료+미수령 업적을 전부 한 번에 수령
    @PostMapping("/claim-all")
    public ResponseEntity<ApiResponse<ClaimAllAchievementsResponse>> claimAllAchievements(
            @RequestBody ClaimAllAchievementsRequest request,
            @CommanderId Long actualCommanderId) {
        ClaimAllAchievementsResponse response = achievementService.claimAllAchievements(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
