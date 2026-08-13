package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.FleetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fleet")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    // 캐릭터의 모든 함대 목록 조회
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FleetInfoDto>>> getUserFleets(@CommanderId Long actualCommanderId) {
        List<FleetInfoDto> fleets = fleetService.getUserFleets(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(fleets));
    }

    // 특정 함대 상세 조회
    @GetMapping("/{fleetId}")
    public ResponseEntity<ApiResponse<FleetInfoDto>> getFleetDetail(@PathVariable("fleetId") Long fleetId, @CommanderId Long actualCommanderId) {
        FleetInfoDto fleet = fleetService.getFleetDetail(actualCommanderId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(fleet));
    }

    // 활성 함대 조회
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<FleetInfoDto>> getActiveFleet(@CommanderId Long actualCommanderId) {
        FleetInfoDto fleet = fleetService.getActiveFleet(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(fleet));
    }

//    // 새 함대 생성
//    @PostMapping("/create")
//    public ResponseEntity<ApiResponse<FleetInfoDto>> createFleet(
//            @RequestBody CreateFleetRequest createRequest,
//            HttpServletRequest request) {
//            Long actualCommanderId = getCommanderIdFromToken(request);
//            FleetInfoDto fleet = fleetService.createFleet(actualCommanderId, createRequest.getFleetName(), createRequest.getDescription());
//            return ResponseEntity.ok(ApiResponse.success(fleet));
//    }

    // 함대 활성화
    @PostMapping("/{fleetId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateFleet(@PathVariable("fleetId") Long fleetId, @CommanderId Long actualCommanderId) {
        fleetService.activateFleet(actualCommanderId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

//    // 함대 데이터 내보내기 (Export)
//    @GetMapping("/{fleetId}/export")
//    public ResponseEntity<ApiResponse<FleetExportResponse>> exportFleet(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
//            Long actualCommanderId = getCommanderIdFromToken(request);
//            FleetExportResponse exportData = fleetService.exportFleet(actualCommanderId, fleetId);
//            return ResponseEntity.ok(ApiResponse.success(exportData));
//    }

//    // 함대 데이터 가져오기 (Import) - 새 함대 생성
//    @PostMapping("/import")
//    public ResponseEntity<ApiResponse<FleetInfoDto>> importFleet(
//            @RequestBody FleetImportRequest importRequest,
//            HttpServletRequest request) {
//            Long actualCommanderId = getCommanderIdFromToken(request);
//            FleetInfoDto fleet = fleetService.importFleet(actualCommanderId, importRequest);
//            return ResponseEntity.ok(ApiResponse.success(fleet));
//    }

//    // 함대 데이터 업데이트 (Import) - 기존 함대 수정
//    @PutMapping("/{fleetId}/import")
//    public ResponseEntity<ApiResponse<FleetInfoDto>> updateFleetFromImport(@PathVariable("fleetId") Long fleetId, @RequestBody FleetImportRequest importRequest, HttpServletRequest request) {
//            Long actualCommanderId = getCommanderIdFromToken(request);
//            FleetInfoDto fleet = fleetService.updateFleet(actualCommanderId, fleetId, importRequest);
//            return ResponseEntity.ok(ApiResponse.success(fleet));
//    }

    // 함대 삭제
    @DeleteMapping("/{fleetId}")
    public ResponseEntity<ApiResponse<Void>> deleteFleet( @PathVariable("fleetId") Long fleetId, @CommanderId Long actualCommanderId) {
        fleetService.deleteFleet(actualCommanderId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함선 추가
    @PostMapping("/add-ship")
    public ResponseEntity<ApiResponse<AddShipResponse>> addShip(
            @RequestBody AddShipRequest request,
            @CommanderId Long actualCommanderId) {
        AddShipResponse response = fleetService.addShip(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 편대 변경
    @PostMapping("/change-formation")
    public ResponseEntity<ApiResponse<ChangeFormationResponse>> changeFormation(
            @RequestBody ChangeFormationRequest request,
            @CommanderId Long actualCommanderId) {
        ChangeFormationResponse response = fleetService.changeFormation(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전술 옵션 변경
    @PostMapping("/change-tactic-options")
    public ResponseEntity<ApiResponse<ChangeTacticOptionsResponse>> changeTacticOptions(
            @RequestBody ChangeTacticOptionsRequest request,
            @CommanderId Long actualCommanderId) {
        ChangeTacticOptionsResponse response = fleetService.changeTacticOptions(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 즉시 함대 회복 (미네랄 소모)
    @PostMapping("/instant-repair")
    public ResponseEntity<ApiResponse<FleetInstantRepairResponse>> instantRepairFleet(
            @CommanderId Long actualCommanderId) {
        FleetInstantRepairResponse response = fleetService.instantRepairFleet(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 함대 체력 저장
    @PostMapping("/save-health")
    public ResponseEntity<ApiResponse<Void>> saveFleetHealth(
            @RequestBody FleetHealthSaveRequest request,
            @CommanderId Long actualCommanderId) {
        fleetService.saveFleetHealth(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
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

    // 함선 리셋 및 제거
    @PostMapping("/reset-ship")
    public ResponseEntity<ApiResponse<ShipResetRemoveResponse>> resetAndRemoveShip(
            @RequestBody ShipResetRemoveRequest request,
            @CommanderId Long actualCommanderId) {
        ShipResetRemoveResponse response = fleetService.resetAndRemoveShip(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}




