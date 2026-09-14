package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.DailyAchievementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/achievement-daily")
public class DailyAchievementController {

    private final DailyAchievementService dailyAchievementService;

    public DailyAchievementController(DailyAchievementService dailyAchievementService) {
        this.dailyAchievementService = dailyAchievementService;
    }

    // 일일 업적 전체 목록 + 커맨더별 오늘 진행도/수령 상태
    @PostMapping("/list")
    public ResponseEntity<ApiResponse<GetDailyAchievementListResponse>> getDailyAchievementList(
            @RequestBody GetDailyAchievementListRequest request,
            @CommanderId Long actualCommanderId) {
        GetDailyAchievementListResponse response = dailyAchievementService.getDailyAchievementStatusList(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 완료된 일일 업적 수령(업적포인트 지급)
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<ClaimDailyAchievementResponse>> claimDailyAchievement(
            @RequestBody ClaimDailyAchievementRequest request,
            @CommanderId Long actualCommanderId) {
        ClaimDailyAchievementResponse response = dailyAchievementService.claimDailyAchievement(actualCommanderId, request.getAchievementId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 완료+미수령 일일 업적을 전부 한 번에 수령
    @PostMapping("/claim-all")
    public ResponseEntity<ApiResponse<ClaimAllDailyAchievementsResponse>> claimAllDailyAchievements(
            @RequestBody ClaimAllDailyAchievementsRequest request,
            @CommanderId Long actualCommanderId) {
        ClaimAllDailyAchievementsResponse response = dailyAchievementService.claimAllDailyAchievements(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
