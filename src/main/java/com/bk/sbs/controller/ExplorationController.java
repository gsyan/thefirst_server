package com.bk.sbs.controller;

import com.bk.sbs.dto.AbandonZoneRunRequest;
import com.bk.sbs.dto.AbandonZoneRunResponse;
import com.bk.sbs.dto.ClearExplorationCellRequest;
import com.bk.sbs.dto.ClearExplorationCellResponse;
import com.bk.sbs.dto.ConfirmRewardCardRequest;
import com.bk.sbs.dto.ConfirmRewardCardResponse;
import com.bk.sbs.dto.EnterExplorationCellRequest;
import com.bk.sbs.dto.EnterExplorationCellResponse;
import com.bk.sbs.dto.EscapeExplorationZoneRequest;
import com.bk.sbs.dto.EscapeExplorationZoneResponse;
import com.bk.sbs.dto.GetActiveZoneRunProgressRequest;
import com.bk.sbs.dto.GetActiveZoneRunProgressResponse;
import com.bk.sbs.dto.IncreaseCommandPowerMaxRequest;
import com.bk.sbs.dto.IncreaseCommandPowerMaxResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.ExplorationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/exploration")
public class ExplorationController {

    private final ExplorationService explorationService;
    private final JwtUtil jwtUtil;

    public ExplorationController(ExplorationService explorationService, JwtUtil jwtUtil) {
        this.explorationService = explorationService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/enter-cell")
    public ResponseEntity<ApiResponse<EnterExplorationCellResponse>> enterCell(
            @RequestBody EnterExplorationCellRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        EnterExplorationCellResponse response = explorationService.enterExplorationCell(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/clear-cell")
    public ResponseEntity<ApiResponse<ClearExplorationCellResponse>> clearCell(
            @RequestBody ClearExplorationCellRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        ClearExplorationCellResponse response = explorationService.clearExplorationCell(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/confirm-reward-card")
    public ResponseEntity<ApiResponse<ConfirmRewardCardResponse>> confirmRewardCard(
            @RequestBody ConfirmRewardCardRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        ConfirmRewardCardResponse response = explorationService.confirmRewardCard(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/active-run-progress")
    public ResponseEntity<ApiResponse<GetActiveZoneRunProgressResponse>> getActiveRunProgress(
            @RequestBody GetActiveZoneRunProgressRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        GetActiveZoneRunProgressResponse response = explorationService.getActiveZoneRunProgress(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/escape-zone")
    public ResponseEntity<ApiResponse<EscapeExplorationZoneResponse>> escapeZone(
            @RequestBody EscapeExplorationZoneRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        EscapeExplorationZoneResponse response = explorationService.escapeExplorationZone(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/abandon-run")
    public ResponseEntity<ApiResponse<AbandonZoneRunResponse>> abandonRun(
            @RequestBody AbandonZoneRunRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        AbandonZoneRunResponse response = explorationService.abandonZoneRun(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/increase-command-power")
    public ResponseEntity<ApiResponse<IncreaseCommandPowerMaxResponse>> increaseCommandPowerMax(
            @RequestBody IncreaseCommandPowerMaxRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        int amount = request.getAmount() != null ? request.getAmount() : 0;
        IncreaseCommandPowerMaxResponse response = explorationService.increaseCommandPowerMax(commanderId, amount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // JWT 토큰에서 캐릭터 ID 추출 (비트 마스킹 포함) — ZoneController와 동일 패턴
    private Long getCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.EXPLORATION_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCommanderId(token) == false) throw new BusinessException(ServerErrorCode.EXPLORATION_CONTROLLER_FAIL_JWT_HAS_COMMANDERID);

        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.EXPLORATION_CONTROLLER_FAIL_JWT_GET_COMMANDERID);

        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}
