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
        Long actualCharacterId = getCharacterIdFromToken(request);
        List<FleetInfoDto> fleets = fleetService.getUserFleets(actualCharacterId);
        return ResponseEntity.ok(ApiResponse.success(fleets));
    }

    // 특정 함대 상세 조회
    @GetMapping("/{fleetId}")
    public ResponseEntity<ApiResponse<FleetInfoDto>> getFleetDetail(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        Long actualCharacterId = getCharacterIdFromToken(request);
        FleetInfoDto fleet = fleetService.getFleetDetail(actualCharacterId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(fleet));
    }

    // 활성 함대 조회
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<FleetInfoDto>> getActiveFleet(HttpServletRequest request) {
        Long actualCharacterId = getCharacterIdFromToken(request);
        FleetInfoDto fleet = fleetService.getActiveFleet(actualCharacterId);
        return ResponseEntity.ok(ApiResponse.success(fleet));
    }

//    // 새 함대 생성
//    @PostMapping("/create")
//    public ResponseEntity<ApiResponse<FleetInfoDto>> createFleet(
//            @RequestBody CreateFleetRequest createRequest,
//            HttpServletRequest request) {
//            Long actualCharacterId = getCharacterIdFromToken(request);
//            FleetInfoDto fleet = fleetService.createFleet(actualCharacterId, createRequest.getFleetName(), createRequest.getDescription());
//            return ResponseEntity.ok(ApiResponse.success(fleet));
//    }

    // 함대 활성화
    @PostMapping("/{fleetId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateFleet(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        Long actualCharacterId = getCharacterIdFromToken(request);
        fleetService.activateFleet(actualCharacterId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

//    // 함대 데이터 내보내기 (Export)
//    @GetMapping("/{fleetId}/export")
//    public ResponseEntity<ApiResponse<FleetExportResponse>> exportFleet(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
//            Long actualCharacterId = getCharacterIdFromToken(request);
//            FleetExportResponse exportData = fleetService.exportFleet(actualCharacterId, fleetId);
//            return ResponseEntity.ok(ApiResponse.success(exportData));
//    }

//    // 함대 데이터 가져오기 (Import) - 새 함대 생성
//    @PostMapping("/import")
//    public ResponseEntity<ApiResponse<FleetInfoDto>> importFleet(
//            @RequestBody FleetImportRequest importRequest,
//            HttpServletRequest request) {
//            Long actualCharacterId = getCharacterIdFromToken(request);
//            FleetInfoDto fleet = fleetService.importFleet(actualCharacterId, importRequest);
//            return ResponseEntity.ok(ApiResponse.success(fleet));
//    }

//    // 함대 데이터 업데이트 (Import) - 기존 함대 수정
//    @PutMapping("/{fleetId}/import")
//    public ResponseEntity<ApiResponse<FleetInfoDto>> updateFleetFromImport(@PathVariable("fleetId") Long fleetId, @RequestBody FleetImportRequest importRequest, HttpServletRequest request) {
//            Long actualCharacterId = getCharacterIdFromToken(request);
//            FleetInfoDto fleet = fleetService.updateFleet(actualCharacterId, fleetId, importRequest);
//            return ResponseEntity.ok(ApiResponse.success(fleet));
//    }

    // 함대 삭제
    @DeleteMapping("/{fleetId}")
    public ResponseEntity<ApiResponse<Void>> deleteFleet( @PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        Long actualCharacterId = getCharacterIdFromToken(request);
        fleetService.deleteFleet(actualCharacterId, fleetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 함선 추가
    @PostMapping("/add-ship")
    public ResponseEntity<ApiResponse<AddShipResponse>> addShip(
            @RequestBody AddShipRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        AddShipResponse response = fleetService.addShip(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 업그레이드
    @PostMapping("/upgrade-module")
    public ResponseEntity<ApiResponse<ModuleUpgradeResponse>> upgradeModule(
            @RequestBody ModuleUpgradeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        ModuleUpgradeResponse response = fleetService.upgradeModule(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 교체
    @PostMapping("/change-module")
    public ResponseEntity<ApiResponse<ModuleChangeResponse>> changeModule(
            @RequestBody ModuleChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        ModuleChangeResponse response = fleetService.changeModule(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 해금
    @PostMapping("/unlock-module")
    public ResponseEntity<ApiResponse<ModuleUnlockResponse>> unlockModule(
            @RequestBody ModuleUnlockRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        ModuleUnlockResponse response = fleetService.unlockModule(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 개발(연구)
    @PostMapping("/research-module")
    public ResponseEntity<ApiResponse<ModuleResearchResponse>> researchModule(
            @RequestBody ModuleResearchRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        ModuleResearchResponse response = fleetService.researchModule(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 편대 변경
    @PostMapping("/change-formation")
    public ResponseEntity<ApiResponse<ChangeFormationResponse>> changeFormation(
            @RequestBody ChangeFormationRequest request,
            HttpServletRequest httpRequest) {
        Long actualCharacterId = getCharacterIdFromToken(httpRequest);
        ChangeFormationResponse response = fleetService.changeFormation(actualCharacterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // JWT 토큰에서 캐릭터 ID 추출 (비트 마스킹 포함)
    private Long getCharacterIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.FLEET_CONTROLLER_FAIL_INVALID_TOKEN);
        if (jwtUtil.hasCharacterId(token) == false) throw new BusinessException(ServerErrorCode.FLEET_CONTROLLER_FAIL_JWT_HAS_CHARACTERID);

        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) throw new BusinessException(ServerErrorCode.FLEET_CONTROLLER_FAIL_JWT_GET_CHARACTERID);

        // characterId에서 실제 character ID 추출 (하위 56비트)
        return characterId & 0x00FFFFFFFFFFFFFFL;
    }
}
