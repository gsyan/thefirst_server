package com.bk.sbs.controller;

import com.bk.sbs.dto.*;
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
    public ResponseEntity<ApiResponse<List<FleetDto>>> getUserFleets(HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            List<FleetDto> fleets = fleetService.getUserFleets(actualCharacterId);
            return ResponseEntity.ok(ApiResponse.success(fleets));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 특정 함대 상세 조회
    @GetMapping("/{fleetId}")
    public ResponseEntity<ApiResponse<FleetDto>> getFleetDetail(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            FleetDto fleet = fleetService.getFleetDetail(actualCharacterId, fleetId);
            return ResponseEntity.ok(ApiResponse.success(fleet));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 활성 함대 조회
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<FleetDto>> getActiveFleet(HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            FleetDto fleet = fleetService.getActiveFleet(actualCharacterId);
            return ResponseEntity.ok(ApiResponse.success(fleet));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 새 함대 생성
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<FleetDto>> createFleet(
            @RequestBody CreateFleetRequest createRequest,
            HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            FleetDto fleet = fleetService.createFleet(actualCharacterId, createRequest.getFleetName(), createRequest.getDescription());
            return ResponseEntity.ok(ApiResponse.success(fleet));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 함대 활성화
    @PostMapping("/{fleetId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateFleet(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            fleetService.activateFleet(actualCharacterId, fleetId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 함대 데이터 내보내기 (Export)
    @GetMapping("/{fleetId}/export")
    public ResponseEntity<ApiResponse<FleetExportResponse>> exportFleet(@PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            FleetExportResponse exportData = fleetService.exportFleet(actualCharacterId, fleetId);
            return ResponseEntity.ok(ApiResponse.success(exportData));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 함대 데이터 가져오기 (Import) - 새 함대 생성
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<FleetDto>> importFleet(
            @RequestBody FleetImportRequest importRequest,
            HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            FleetDto fleet = fleetService.importFleet(actualCharacterId, importRequest);
            return ResponseEntity.ok(ApiResponse.success(fleet));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 함대 데이터 업데이트 (Import) - 기존 함대 수정
    @PutMapping("/{fleetId}/import")
    public ResponseEntity<ApiResponse<FleetDto>> updateFleetFromImport(@PathVariable("fleetId") Long fleetId, @RequestBody FleetImportRequest importRequest, HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            FleetDto fleet = fleetService.updateFleet(actualCharacterId, fleetId, importRequest);
            return ResponseEntity.ok(ApiResponse.success(fleet));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 함대 삭제
    @DeleteMapping("/{fleetId}")
    public ResponseEntity<ApiResponse<Void>> deleteFleet( @PathVariable("fleetId") Long fleetId, HttpServletRequest request) {
        try {
            Long characterId = getCharacterIdFromToken(request);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            fleetService.deleteFleet(actualCharacterId, fleetId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 함선 추가
    @PostMapping("/add-ship")
    public ResponseEntity<ApiResponse<AddShipResponse>> addShip(
            @RequestBody AddShipRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long characterId = getCharacterIdFromToken(httpRequest);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            AddShipResponse response = fleetService.addShip(actualCharacterId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 모듈 업그레이드
    @PostMapping("/upgrade-module")
    public ResponseEntity<ApiResponse<ModuleUpgradeResponse>> upgradeModule(
            @RequestBody ModuleUpgradeRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long characterId = getCharacterIdFromToken(httpRequest);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            ModuleUpgradeResponse response = fleetService.upgradeModule(actualCharacterId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 모듈 교체
    @PostMapping("/change-module")
    public ResponseEntity<ApiResponse<ModuleChangeResponse>> changeModule(
            @RequestBody ModuleChangeRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long characterId = getCharacterIdFromToken(httpRequest);
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            ModuleChangeResponse response = fleetService.changeModule(actualCharacterId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 모듈 해금
    @PostMapping("/unlock-module")
    public ResponseEntity<ApiResponse<ModuleUnlockResponse>> unlockModule(
            @RequestBody ModuleUnlockRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long characterId = getCharacterIdFromToken(httpRequest);
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            ModuleUnlockResponse response = fleetService.unlockModule(actualCharacterId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 모듈 개발(연구)
    @PostMapping("/research-module")
    public ResponseEntity<ApiResponse<ModuleResearchResponse>> researchModule(
            @RequestBody ModuleResearchRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long characterId = getCharacterIdFromToken(httpRequest);
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            ModuleResearchResponse response = fleetService.researchModule(actualCharacterId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // 편대 변경
    @PostMapping("/change-formation")
    public ResponseEntity<ApiResponse<ChangeFormationResponse>> changeFormation(
            @RequestBody ChangeFormationRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long characterId = getCharacterIdFromToken(httpRequest);
            // characterId에서 실제 character ID 추출 (하위 56비트)
            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
            ChangeFormationResponse response = fleetService.changeFormation(actualCharacterId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR));
        }
    }

    // JWT 토큰에서 캐릭터 ID 추출
    private Long getCharacterIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) {
            throw new RuntimeException("Authentication token required.");
        }
        
        if (!jwtUtil.hasCharacterId(token)) {
            throw new RuntimeException("Character selection required. Please refresh your token.");
        }
        
        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) {
            throw new RuntimeException("Invalid token.");
        }
        
        return characterId;
    }
}
