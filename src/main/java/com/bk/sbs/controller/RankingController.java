package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.PvpService;
import com.bk.sbs.service.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;
    private final PvpService pvpService;

    public RankingController(RankingService rankingService, PvpService pvpService) {
        this.rankingService = rankingService;
        this.pvpService = pvpService;
    }

    // PVP 랭킹 보드 페이지 조회
    @PostMapping("/pvp")
    public ResponseEntity<ApiResponse<PvpRankingResponse>> getPvpRanking(
            @RequestBody PvpRankingRequest request,
            @CommanderId Long commanderId) {
        PvpRankingResponse response = rankingService.getPvpRanking(request.getOffset(), request.getLimit(), commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 내 PVP 랭크 조회
    @PostMapping("/pvp/my-rank")
    public ResponseEntity<ApiResponse<PvpMyRankResponse>> getMyPvpRank(
            @RequestBody PvpMyRankRequest request,
            @CommanderId Long commanderId) {
        PvpMyRankResponse response = pvpService.getMyRank(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Zone 랭킹 보드 페이지 조회
    @PostMapping("/zone")
    public ResponseEntity<ApiResponse<ZoneRankingResponse>> getZoneRanking(
            @RequestBody ZoneRankingRequest request,
            @CommanderId Long commanderId) {
        ZoneRankingResponse response = rankingService.getZoneRanking(request.getOffset(), request.getLimit(), commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}



