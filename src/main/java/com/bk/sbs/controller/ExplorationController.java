package com.bk.sbs.controller;

import com.bk.sbs.dto.AbandonZoneRunRequest;
import com.bk.sbs.dto.AbandonZoneRunResponse;
import com.bk.sbs.dto.ChargeResourceRequest;
import com.bk.sbs.dto.ChargeResourceResponse;
import com.bk.sbs.dto.ClearExplorationCellRequest;
import com.bk.sbs.dto.ClearExplorationCellResponse;
import com.bk.sbs.dto.CloseRewardCardPopupRequest;
import com.bk.sbs.dto.CloseRewardCardPopupResponse;
import com.bk.sbs.dto.EnterExplorationCellRequest;
import com.bk.sbs.dto.EnterExplorationCellResponse;
import com.bk.sbs.dto.EscapeExplorationZoneRequest;
import com.bk.sbs.dto.EscapeExplorationZoneResponse;
import com.bk.sbs.dto.GetActiveZoneRunProgressRequest;
import com.bk.sbs.dto.GetActiveZoneRunProgressResponse;
import com.bk.sbs.dto.GetRewardCardShopRequest;
import com.bk.sbs.dto.GetRewardCardShopResponse;
import com.bk.sbs.dto.MoveToExplorationCellRequest;
import com.bk.sbs.dto.MoveToExplorationCellResponse;
import com.bk.sbs.dto.PurchaseRewardCardRequest;
import com.bk.sbs.dto.PurchaseRewardCardResponse;
import com.bk.sbs.dto.RerollRewardCardRequest;
import com.bk.sbs.dto.RerollRewardCardResponse;
import com.bk.sbs.dto.WatchAdForRewardCardRerollChargeRequest;
import com.bk.sbs.dto.WatchAdForRewardCardRerollChargeResponse;
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

    @PostMapping("/move-to-cell")
    public ResponseEntity<ApiResponse<MoveToExplorationCellResponse>> moveToCell(
            @RequestBody MoveToExplorationCellRequest request,
            @CommanderId Long commanderId) {
        MoveToExplorationCellResponse response = explorationService.moveToExplorationCell(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/clear-cell")
    public ResponseEntity<ApiResponse<ClearExplorationCellResponse>> clearCell(
            @RequestBody ClearExplorationCellRequest request,
            @CommanderId Long commanderId) {
        ClearExplorationCellResponse response = explorationService.clearExplorationCell(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reward-card-shop")
    public ResponseEntity<ApiResponse<GetRewardCardShopResponse>> getRewardCardShop(
            @RequestBody GetRewardCardShopRequest request,
            @CommanderId Long commanderId) {
        GetRewardCardShopResponse response = explorationService.getRewardCardShop(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/purchase-reward-card")
    public ResponseEntity<ApiResponse<PurchaseRewardCardResponse>> purchaseRewardCard(
            @RequestBody PurchaseRewardCardRequest request,
            @CommanderId Long commanderId) {
        PurchaseRewardCardResponse response = explorationService.purchaseRewardCard(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/close-reward-card-popup")
    public ResponseEntity<ApiResponse<CloseRewardCardPopupResponse>> closeRewardCardPopup(
            @RequestBody CloseRewardCardPopupRequest request,
            @CommanderId Long commanderId) {
        CloseRewardCardPopupResponse response = explorationService.closeRewardCardPopup(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/watch-ad-reward-card-reroll-charge")
    public ResponseEntity<ApiResponse<WatchAdForRewardCardRerollChargeResponse>> watchAdForRewardCardRerollCharge(
            @RequestBody WatchAdForRewardCardRerollChargeRequest request,
            @CommanderId Long commanderId) {
        WatchAdForRewardCardRerollChargeResponse response = explorationService.watchAdForRewardCardRerollCharge(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reroll-reward-card")
    public ResponseEntity<ApiResponse<RerollRewardCardResponse>> rerollRewardCard(
            @RequestBody RerollRewardCardRequest request,
            @CommanderId Long commanderId) {
        RerollRewardCardResponse response = explorationService.rerollRewardCard(commanderId, request);
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
        AbandonZoneRunResponse response = explorationService.abandonZoneRun(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/charge-resource")
    public ResponseEntity<ApiResponse<ChargeResourceResponse>> chargeResource(
            @RequestBody ChargeResourceRequest request,
            @CommanderId Long commanderId) {
        ChargeResourceResponse response = explorationService.chargeResource(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
