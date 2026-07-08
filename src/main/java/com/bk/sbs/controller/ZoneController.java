package com.bk.sbs.controller;

import com.bk.sbs.dto.ClearZoneStageRequest;
import com.bk.sbs.dto.ClearZoneStageResponse;
import com.bk.sbs.dto.ClaimZoneRewardRequest;
import com.bk.sbs.dto.ClaimZoneRewardResponse;
import com.bk.sbs.dto.GetStageEnemiesRequest;
import com.bk.sbs.dto.GetStageEnemiesResponse;
import com.bk.sbs.dto.HeartbeatRequest;
import com.bk.sbs.dto.HeartbeatResponse;
import com.bk.sbs.dto.PendingStageRewardRequest;
import com.bk.sbs.dto.PendingStageRewardResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.ZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/zone")
public class ZoneController {

    private final ZoneService zoneService;
    private final JwtUtil jwtUtil;

    public ZoneController(ZoneService zoneService, JwtUtil jwtUtil) {
        this.zoneService = zoneService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/get-stage-enemies")
    public ResponseEntity<ApiResponse<GetStageEnemiesResponse>> getStageEnemies(
            @RequestBody GetStageEnemiesRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        GetStageEnemiesResponse response = zoneService.getStageEnemies(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 웨이브 1개 처치 보고 — 킬 보상 + 클리어 판정
    @PostMapping("/clear-stage")
    public ResponseEntity<ApiResponse<ClearZoneStageResponse>> destroyZoneStageWave(
            @RequestBody ClearZoneStageRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ClearZoneStageResponse response = zoneService.clearZoneStage(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/claim-reward")
    public ResponseEntity<ApiResponse<ClaimZoneRewardResponse>> claimZoneReward(
            @RequestBody ClaimZoneRewardRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ClaimZoneRewardResponse response = zoneService.claimZoneReward(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/claim-pending-rewards")
    public ResponseEntity<ApiResponse<PendingStageRewardResponse>> claimPendingStageRewards(
            @RequestBody PendingStageRewardRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        PendingStageRewardResponse response = zoneService.claimPendingStageRewards(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 하트비트 (온라인 시간 갱신)
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<HeartbeatResponse>> heartbeat(
            @RequestBody HeartbeatRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        HeartbeatResponse response = zoneService.heartbeat(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // JWT 토큰에서 캐릭터 ID 추출 (비트 마스킹 포함)
    private Long getCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.ZONE_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCommanderId(token) == false) throw new BusinessException(ServerErrorCode.ZONE_CONTROLLER_FAIL_JWT_HAS_COMMANDERID);

        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.ZONE_CONTROLLER_FAIL_JWT_GET_COMMANDERID);

        // commanderId에서 실제 Commander ID 추출 (하위 56비트)
        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}




