package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.PvpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/pvp")
public class PvpController {

    private final PvpService pvpService;
    private final JwtUtil jwtUtil;

    public PvpController(PvpService pvpService, JwtUtil jwtUtil) {
        this.pvpService = pvpService;
        this.jwtUtil = jwtUtil;
    }

    // 대전 상대 리스트 조회
    @PostMapping("/list")
    public ResponseEntity<ApiResponse<PvpListResponse>> getOpponentList(
            @RequestBody PvpListRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        PvpListResponse response = pvpService.getOpponentList(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상대 리스트 새로고침
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PvpRefreshResponse>> refreshOpponentList(
            @RequestBody PvpRefreshRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        PvpRefreshResponse response = pvpService.refreshOpponentList(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전투 시작
    @PostMapping("/battle/start")
    public ResponseEntity<ApiResponse<PvpBattleStartResponse>> startBattle(
            @RequestBody PvpBattleStartRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        PvpBattleStartResponse response = pvpService.startBattle(commanderId, request.getOpponentCommanderId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전투 결과 보고
    @PostMapping("/battle/result")
    public ResponseEntity<ApiResponse<PvpBattleResultResponse>> reportBattleResult(
            @RequestBody PvpBattleResultRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        PvpBattleResultResponse response = pvpService.reportBattleResult(
                commanderId, request.getBattleToken(), request.getIsVictory());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 랭킹 관련 엔드포인트는 RankingController(/api/ranking)로 이전

    private Long getCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.PVP_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCommanderId(token) == false) throw new BusinessException(ServerErrorCode.PVP_CONTROLLER_FAIL_JWT_HAS_COMMANDERID);

        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.PVP_CONTROLLER_FAIL_JWT_GET_COMMANDERID);

        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}



