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
import com.bk.sbs.dto.IncreaseTacticPowerMaxRequest;
import com.bk.sbs.dto.IncreaseTacticPowerMaxResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.ExplorationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exploration")
public class ExplorationController {

    private final ExplorationService explorationService;

    public ExplorationController(ExplorationService explorationService) {
        this.explorationService = explorationService;
    }

    @PostMapping("/enter-cell")
    public ResponseEntity<ApiResponse<EnterExplorationCellResponse>> enterCell(
            @RequestBody EnterExplorationCellRequest request,
            @CommanderId Long commanderId) {
        EnterExplorationCellResponse response = explorationService.enterExplorationCell(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/clear-cell")
    public ResponseEntity<ApiResponse<ClearExplorationCellResponse>> clearCell(
            @RequestBody ClearExplorationCellRequest request,
            @CommanderId Long commanderId) {
        ClearExplorationCellResponse response = explorationService.clearExplorationCell(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/confirm-reward-card")
    public ResponseEntity<ApiResponse<ConfirmRewardCardResponse>> confirmRewardCard(
            @RequestBody ConfirmRewardCardRequest request,
            @CommanderId Long commanderId) {
        ConfirmRewardCardResponse response = explorationService.confirmRewardCard(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/active-run-progress")
    public ResponseEntity<ApiResponse<GetActiveZoneRunProgressResponse>> getActiveRunProgress(
            @RequestBody GetActiveZoneRunProgressRequest request,
            @CommanderId Long commanderId) {
        GetActiveZoneRunProgressResponse response = explorationService.getActiveZoneRunProgress(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/escape-zone")
    public ResponseEntity<ApiResponse<EscapeExplorationZoneResponse>> escapeZone(
            @RequestBody EscapeExplorationZoneRequest request,
            @CommanderId Long commanderId) {
        EscapeExplorationZoneResponse response = explorationService.escapeExplorationZone(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/abandon-run")
    public ResponseEntity<ApiResponse<AbandonZoneRunResponse>> abandonRun(
            @RequestBody AbandonZoneRunRequest request,
            @CommanderId Long commanderId) {
        AbandonZoneRunResponse response = explorationService.abandonZoneRun(commanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/increase-command-power")
    public ResponseEntity<ApiResponse<IncreaseCommandPowerMaxResponse>> increaseCommandPowerMax(
            @RequestBody IncreaseCommandPowerMaxRequest request,
            @CommanderId Long commanderId) {
        int amount = request.getAmount() != null ? request.getAmount() : 0;
        IncreaseCommandPowerMaxResponse response = explorationService.increaseCommandPowerMax(commanderId, amount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/increase-tactic-power")
    public ResponseEntity<ApiResponse<IncreaseTacticPowerMaxResponse>> increaseTacticPowerMax(
            @RequestBody IncreaseTacticPowerMaxRequest request,
            @CommanderId Long commanderId) {
        int amount = request.getAmount() != null ? request.getAmount() : 0;
        IncreaseTacticPowerMaxResponse response = explorationService.increaseTacticPowerMax(commanderId, amount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
