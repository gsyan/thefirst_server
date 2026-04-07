package com.bk.sbs.controller;

import com.bk.sbs.dto.ClearZoneStageRequest;
import com.bk.sbs.dto.ClearZoneStageResponse;
import com.bk.sbs.dto.ZoneCollectRequest;
import com.bk.sbs.dto.ZoneCollectResponse;
import com.bk.sbs.dto.ZoneCheckEverClearedRequest;
import com.bk.sbs.dto.ZoneCheckEverClearedResponse;
import com.bk.sbs.dto.HeartbeatRequest;
import com.bk.sbs.dto.HeartbeatResponse;
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

    // 웨이브 1개 처치 보고 — 킬 보상 + 클리어 판정
    @PostMapping("/clear-stage")
    public ResponseEntity<ApiResponse<ClearZoneStageResponse>> destroyZoneStageWave(
            @RequestBody ClearZoneStageRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        ClearZoneStageResponse response = zoneService.clearZoneStage(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Zone 자원 수확
    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<ZoneCollectResponse>> collectZone(
            @RequestBody ZoneCollectRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        ZoneCollectResponse response = zoneService.collectZone(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 하트비트 (온라인 시간 갱신)
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<HeartbeatResponse>> heartbeat(
            @RequestBody HeartbeatRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        HeartbeatResponse response = zoneService.heartbeat(actualCharacterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 존 클리어 이력 조회 — isRestored 무관, 광고 면제 판단용
    @PostMapping("/check-ever-cleared")
    public ResponseEntity<ApiResponse<ZoneCheckEverClearedResponse>> checkEverCleared(
            @RequestBody ZoneCheckEverClearedRequest request,
            HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        boolean everCleared = zoneService.checkEverCleared(characterId, request.getZoneName());
        return ResponseEntity.ok(ApiResponse.success(ZoneCheckEverClearedResponse.builder().everCleared(everCleared).build()));
    }

    // JWT 토큰에서 캐릭터 ID 추출 (비트 마스킹 포함)
    private Long getCharacterIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.ZONE_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCharacterId(token) == false) throw new BusinessException(ServerErrorCode.ZONE_CONTROLLER_FAIL_JWT_HAS_CHARACTERID);

        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) throw new BusinessException(ServerErrorCode.ZONE_CONTROLLER_FAIL_JWT_GET_CHARACTERID);

        // characterId에서 실제 character ID 추출 (하위 56비트)
        return characterId & 0x00FFFFFFFFFFFFFFL;
    }
}
