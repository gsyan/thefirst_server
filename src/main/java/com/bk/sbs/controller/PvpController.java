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
        Long characterId = getCharacterIdFromToken(httpRequest);
        PvpListResponse response = pvpService.getOpponentList(characterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상대 리스트 새로고침
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PvpRefreshResponse>> refreshOpponentList(
            @RequestBody PvpRefreshRequest request,
            HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        PvpRefreshResponse response = pvpService.refreshOpponentList(characterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전투 시작
    @PostMapping("/battle/start")
    public ResponseEntity<ApiResponse<PvpBattleStartResponse>> startBattle(
            @RequestBody PvpBattleStartRequest request,
            HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        PvpBattleStartResponse response = pvpService.startBattle(characterId, request.getOpponentCharacterId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전투 결과 보고
    @PostMapping("/battle/result")
    public ResponseEntity<ApiResponse<PvpBattleResultResponse>> reportBattleResult(
            @RequestBody PvpBattleResultRequest request,
            HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        PvpBattleResultResponse response = pvpService.reportBattleResult(
                characterId, request.getBattleToken(), request.getIsVictory());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long getCharacterIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.PVP_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCharacterId(token) == false) throw new BusinessException(ServerErrorCode.PVP_CONTROLLER_FAIL_JWT_HAS_CHARACTERID);

        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) throw new BusinessException(ServerErrorCode.PVP_CONTROLLER_FAIL_JWT_GET_CHARACTERID);

        return characterId & 0x00FFFFFFFFFFFFFFL;
    }
}
