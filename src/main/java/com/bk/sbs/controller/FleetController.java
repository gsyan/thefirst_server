package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.FleetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fleet")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    // 전술 옵션 변경
    @PostMapping("/change-tactic-options")
    public ResponseEntity<ApiResponse<ChangeTacticOptionsResponse>> changeTacticOptions(
            @RequestBody ChangeTacticOptionsRequest request,
            @CommanderId Long actualCommanderId) {
        ChangeTacticOptionsResponse response = fleetService.changeTacticOptions(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 함대편성(FleetComposition) 슬롯에 함선 배치/교체 저장
    @PostMapping("/preset/place-ship")
    public ResponseEntity<ApiResponse<Void>> placeFleetPresetShip(
            @RequestBody FleetPresetPlaceShipRequest request,
            @CommanderId Long actualCommanderId) {
        fleetService.placeFleetPresetShip(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함대편성 슬롯 전/후방 토글 저장
    @PostMapping("/preset/set-front")
    public ResponseEntity<ApiResponse<Void>> setFleetPresetShipFront(
            @RequestBody FleetPresetSetFrontRequest request,
            @CommanderId Long actualCommanderId) {
        fleetService.setFleetPresetShipFront(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함대편성 슬롯(함선) 하나의 장착 모듈 전체를 최종 상태로 한 번에 교체(on/off) — 예산/공격모듈 0개는 이 결과 상태 기준으로 검증
    @PostMapping("/preset/set-modules")
    public ResponseEntity<ApiResponse<SetFleetPresetSlotModulesResponse>> setFleetPresetSlotModules(
            @RequestBody SetFleetPresetSlotModulesRequest request,
            @CommanderId Long actualCommanderId) {
        SetFleetPresetSlotModulesResponse response = fleetService.setFleetPresetSlotModules(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}




