package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.FleetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/fleet")
public class FleetController {

    private final FleetService fleetService;
    private final JwtUtil jwtUtil;

    public FleetController(FleetService fleetService, JwtUtil jwtUtil) {
        this.fleetService = fleetService;
        this.jwtUtil = jwtUtil;
    }

    // 캐릭터의 모든 함대 목록 조회
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FleetInfoDto>>> getUserFleets(HttpServletRequest request) {
        Long actualCommanderId = getCommanderIdFromToken(request);
        List<FleetInfoDto> fleets = fleetService.getUserFleets(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(fleets));
    }

    // 특정 함대 상세 조회
    @GetMapping("/{fleetId}")
    public ResponseEntity<ApiResponse<FleetInfoDto>> getFleetDetail(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        Long actualCommanderId = getCommanderIdFromToken(request);
        FleetInfoDto fleet = fleetService.getFleetDetail(actualCommanderId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(fleet));
    }

    // 활성 함대 조회
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<FleetInfoDto>> getActiveFleet(HttpServletRequest request) {
        Long actualCommanderId = getCommanderIdFromToken(request);
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
    public ResponseEntity<ApiResponse<Void>> activateFleet(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        Long actualCommanderId = getCommanderIdFromToken(request);
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
    public ResponseEntity<ApiResponse<Void>> deleteFleet( @PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        Long actualCommanderId = getCommanderIdFromToken(request);
        fleetService.deleteFleet(actualCommanderId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함선 추가
    @PostMapping("/add-ship")
    public ResponseEntity<ApiResponse<AddShipResponse>> addShip(
            @RequestBody AddShipRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        AddShipResponse response = fleetService.addShip(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 편대 변경
    @PostMapping("/change-formation")
    public ResponseEntity<ApiResponse<ChangeFormationResponse>> changeFormation(
            @RequestBody ChangeFormationRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ChangeFormationResponse response = fleetService.changeFormation(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전술 옵션 변경
    @PostMapping("/change-tactic-options")
    public ResponseEntity<ApiResponse<ChangeTacticOptionsResponse>> changeTacticOptions(
            @RequestBody ChangeTacticOptionsRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ChangeTacticOptionsResponse response = fleetService.changeTacticOptions(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 즉시 함대 회복 (미네랄 소모)
    @PostMapping("/instant-repair")
    public ResponseEntity<ApiResponse<FleetInstantRepairResponse>> instantRepairFleet(
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        FleetInstantRepairResponse response = fleetService.instantRepairFleet(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 함대 체력 저장
    @PostMapping("/save-health")
    public ResponseEntity<ApiResponse<Void>> saveFleetHealth(
            @RequestBody FleetHealthSaveRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        fleetService.saveFleetHealth(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함대편성(FleetComposition) 슬롯에 함선 배치/교체 저장
    @PostMapping("/preset/place-ship")
    public ResponseEntity<ApiResponse<Void>> placeFleetPresetShip(
            @RequestBody FleetPresetPlaceShipRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        fleetService.placeFleetPresetShip(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함대편성 슬롯 전/후방 토글 저장
    @PostMapping("/preset/set-front")
    public ResponseEntity<ApiResponse<Void>> setFleetPresetShipFront(
            @RequestBody FleetPresetSetFrontRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        fleetService.setFleetPresetShipFront(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함선 리셋 및 제거
    @PostMapping("/reset-ship")
    public ResponseEntity<ApiResponse<ShipResetRemoveResponse>> resetAndRemoveShip(
            @RequestBody ShipResetRemoveRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ShipResetRemoveResponse response = fleetService.resetAndRemoveShip(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // JWT 토큰에서 캐릭터 ID 추출 (비트 마스킹 포함)
    private Long getCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.FLEET_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCommanderId(token) == false) throw new BusinessException(ServerErrorCode.FLEET_CONTROLLER_FAIL_JWT_HAS_COMMANDERID);

        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.FLEET_CONTROLLER_FAIL_JWT_GET_COMMANDERID);

        // commanderId에서 실제 commander ID 추출 (하위 56비트)
        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}




