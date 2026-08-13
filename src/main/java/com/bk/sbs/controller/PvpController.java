package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.PvpService;
import com.bk.sbs.service.PvpSeasonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pvp")
public class PvpController {

    private final PvpService pvpService;
    private final PvpSeasonService pvpSeasonService;

    public PvpController(PvpService pvpService, PvpSeasonService pvpSeasonService) {
        this.pvpService = pvpService;
        this.pvpSeasonService = pvpSeasonService;
    }

    // 대전 상대 리스트 조회
    @PostMapping("/list")
    public ResponseEntity<ApiResponse<PvpListResponse>> getOpponentList(
            @RequestBody PvpListRequest request,
            @CommanderId Long commanderId) {
        PvpListResponse response = pvpService.getOpponentList(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상대 리스트 새로고침
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PvpRefreshResponse>> refreshOpponentList(
            @RequestBody PvpRefreshRequest request,
            @CommanderId Long commanderId) {
        PvpRefreshResponse response = pvpService.refreshOpponentList(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전투 시작
    @PostMapping("/battle/start")
    public ResponseEntity<ApiResponse<PvpBattleStartResponse>> startBattle(
            @RequestBody PvpBattleStartRequest request,
            @CommanderId Long commanderId) {
        PvpBattleStartResponse response = pvpService.startBattle(commanderId, request.getOpponentCommanderId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전투 결과 보고
    @PostMapping("/battle/result")
    public ResponseEntity<ApiResponse<PvpBattleResultResponse>> reportBattleResult(
            @RequestBody PvpBattleResultRequest request,
            @CommanderId Long commanderId) {
        PvpBattleResultResponse response = pvpService.reportBattleResult(
                commanderId, request.getBattleToken(), request.getIsVictory());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 시즌 보상 수령
    @PostMapping("/pvp-season/claim-reward")
    public ResponseEntity<ApiResponse<PvpClaimSeasonRewardResponse>> claimSeasonReward(@CommanderId Long commanderId) {
        int reward = pvpSeasonService.claimPendingSeasonReward(commanderId);
        return ResponseEntity.ok(ApiResponse.success(new PvpClaimSeasonRewardResponse(reward)));
    }

    // 랭킹 관련 엔드포인트는 RankingController(/api/ranking)로 이전
}



