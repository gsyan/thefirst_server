package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.PvpService;
import com.bk.sbs.service.RankingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;
    private final PvpService pvpService;
    private final JwtUtil jwtUtil;

    public RankingController(RankingService rankingService, PvpService pvpService, JwtUtil jwtUtil) {
        this.rankingService = rankingService;
        this.pvpService = pvpService;
        this.jwtUtil = jwtUtil;
    }

    // PVP 랭킹 보드 페이지 조회
    @PostMapping("/pvp")
    public ResponseEntity<ApiResponse<PvpRankingResponse>> getPvpRanking(
            @RequestBody PvpRankingRequest request,
            HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        PvpRankingResponse response = rankingService.getPvpRanking(request.getOffset(), request.getLimit(), characterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 내 PVP 랭크 조회
    @PostMapping("/pvp/my-rank")
    public ResponseEntity<ApiResponse<PvpMyRankResponse>> getMyPvpRank(
            @RequestBody PvpMyRankRequest request,
            HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        PvpMyRankResponse response = pvpService.getMyRank(characterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Zone 랭킹 보드 페이지 조회
    @PostMapping("/zone")
    public ResponseEntity<ApiResponse<ZoneRankingResponse>> getZoneRanking(
            @RequestBody ZoneRankingRequest request,
            HttpServletRequest httpRequest) {
        Long characterId = getCharacterIdFromToken(httpRequest);
        ZoneRankingResponse response = rankingService.getZoneRanking(request.getOffset(), request.getLimit(), characterId);
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
