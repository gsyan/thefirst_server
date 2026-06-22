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

    // 모듈 레벨업
    @PostMapping("/levelup-module")
    public ResponseEntity<ApiResponse<ModuleLevelChangeResponse>> moduleLevelUp(
            @RequestBody ModuleLevelChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleLevelChangeResponse response = fleetService.moduleLevelUp(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 레벨다운
    @PostMapping("/leveldown-module")
    public ResponseEntity<ApiResponse<ModuleLevelChangeResponse>> moduleLevelDown(
            @RequestBody ModuleLevelChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleLevelChangeResponse response = fleetService.moduleLevelDown(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 등급 업
    @PostMapping("/gradeup-module")
    public ResponseEntity<ApiResponse<ModuleGradeChangeResponse>> moduleGradeUp(
            @RequestBody ModuleGradeChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleGradeChangeResponse response = fleetService.moduleGradeUp(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 등급 다운
    @PostMapping("/gradedown-module")
    public ResponseEntity<ApiResponse<ModuleGradeChangeResponse>> moduleGradeDown(
            @RequestBody ModuleGradeChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleGradeChangeResponse response = fleetService.moduleGradeDown(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 모듈 해금
    @PostMapping("/unlock-module")
    public ResponseEntity<ApiResponse<ModuleUnlockResponse>> moduleUnlock(
            @RequestBody ModuleUnlockRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleUnlockResponse response = fleetService.moduleUnlock(actualCommanderId, request);
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

    // 모듈 리셋
    @PostMapping("/reset-module")
    public ResponseEntity<ApiResponse<ModuleResetResponse>> resetModule(
            @RequestBody ModuleResetRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleResetResponse response = fleetService.resetModule(actualCommanderId, request);
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

    // 미네랄 모듈 해금
    @PostMapping("/module-unlock-mineral")
    public ResponseEntity<ApiResponse<ModuleUnlockResponse>> moduleUnlockMineral(
            @RequestBody ModuleUnlockRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleUnlockResponse response = fleetService.moduleUnlockMineral(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 미네랄 모듈 레벨업
    @PostMapping("/module-levelup-mineral")
    public ResponseEntity<ApiResponse<ModuleLevelChangeResponse>> moduleLevelUpMineral(
            @RequestBody ModuleLevelChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleLevelChangeResponse response = fleetService.moduleLevelUpMineral(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 미네랄 모듈 레벨다운
    @PostMapping("/module-leveldown-mineral")
    public ResponseEntity<ApiResponse<ModuleLevelChangeResponse>> moduleLevelDownMineral(
            @RequestBody ModuleLevelChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleLevelChangeResponse response = fleetService.moduleLevelDownMineral(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 미네랄 모듈 등급업
    @PostMapping("/module-gradeup-mineral")
    public ResponseEntity<ApiResponse<ModuleGradeChangeResponse>> moduleGradeUpMineral(
            @RequestBody ModuleGradeChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleGradeChangeResponse response = fleetService.moduleGradeUpMineral(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 미네랄 모듈 등급다운
    @PostMapping("/module-gradedown-mineral")
    public ResponseEntity<ApiResponse<ModuleGradeChangeResponse>> moduleGradeDownMineral(
            @RequestBody ModuleGradeChangeRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleGradeChangeResponse response = fleetService.moduleGradeDownMineral(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 미네랄 모듈 리셋 (전체 미네랄 환급)
    @PostMapping("/mineral-reset-module")
    public ResponseEntity<ApiResponse<ModuleResetResponse>> mineralResetModule(
            @RequestBody ModuleResetRequest request,
            HttpServletRequest httpRequest) {
        Long actualCommanderId = getCommanderIdFromToken(httpRequest);
        ModuleResetResponse response = fleetService.mineralResetModule(actualCommanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
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




