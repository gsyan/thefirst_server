//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.AuthResponse;
import com.bk.sbs.dto.CommanderCreateRequest;
import com.bk.sbs.dto.CommanderRenameRequest;
import com.bk.sbs.dto.CommanderRenameResponse;
import com.bk.sbs.dto.CommanderValidateNameRequest;
import com.bk.sbs.dto.CommanderResponse;
import com.bk.sbs.dto.CommanderInfoDto;
import com.bk.sbs.dto.FleetInfoDto;
import com.bk.sbs.dto.VipStatusResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.AccountService;
import com.bk.sbs.service.CommanderService;
import com.bk.sbs.service.FleetService;
import com.bk.sbs.service.IapService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/commander")
public class CommanderController {

    private final AccountService accountService;
    private final CommanderService commanderService;
    private final FleetService fleetService;
    private final IapService iapService;
    private final JwtUtil jwtUtil;

    public CommanderController(AccountService accountService, CommanderService commanderService, FleetService fleetService, IapService iapService, JwtUtil jwtUtil) {
        this.accountService = accountService;
        this.commanderService = commanderService;
        this.fleetService = fleetService;
        this.iapService = iapService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/create")
    public ApiResponse<CommanderResponse> createCommander(@RequestBody CommanderCreateRequest request) {
        CommanderResponse response = commanderService.createCommander(request);
        return ApiResponse.success(response);
    }

    // 커맨더 선택 후 토큰 갱신
    @PostMapping("/select-commander/{commanderId}")
    public ApiResponse<AuthResponse> selectCommander(@PathVariable("commanderId") Long commanderId, HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_TOKEN);
        Long accountId = jwtUtil.getAccountIdFromSubject(token);
        if (accountId == null) throw new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACCOUNTID);
        // commanderId에서 실제 commander ID 추출 (하위 56비트)
        Long actualCommanderId = commanderId & 0x00FFFFFFFFFFFFFFL;

        // 커맨더가 해당 계정에 속하는지 확인
        boolean isValidCommander = accountService.validateCommanderOwnership(accountId, actualCommanderId);
        if (isValidCommander == false) throw new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_INVALID_COMMANDER);

        // 새로운 토큰 생성 (commanderId 포함)
        String newAccessToken = jwtUtil.createAccessTokenWithCommander(accountId, commanderId);
        String newRefreshToken = jwtUtil.createRefreshTokenWithCommander(accountId, commanderId);

        // 활성 함대 정보 조회
        FleetInfoDto activeFleet = fleetService.getActiveFleet(actualCommanderId);

        if (activeFleet == null) throw new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET);

        // 커맨더 상태 정보 조회
        CommanderInfoDto commanderInfoDto = commanderService.getCommanderInfoDto(actualCommanderId);

        // 문자열 기반 연구 ID 조회
        var researchedIds = fleetService.getResearchedIds(actualCommanderId);

        boolean bGoogleLinked = accountService.isGoogleLinked(accountId);
        VipStatusResponse vipStatus = iapService.getVipStatus(actualCommanderId);

        AuthResponse response = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .activeFleetInfo(activeFleet)
                .commanderInfo(commanderInfoDto)
                .researchedIds(researchedIds)
                .bGoogleLinked(bGoogleLinked)
                .vipStatus(vipStatus)
                .build();
        return ApiResponse.success(response);
    }

    @GetMapping("/commanders")
    public ApiResponse<List<CommanderResponse>> getAllCommanders() {
        return accountService.getAllCommanders();
    }

    // 이름 유효성 검사 (실시간 입력 중 호출) — 중복·비속어만 검사, 포맷은 클라에서 처리
    @PostMapping("/validate-name")
    public ApiResponse<Boolean> validateCommanderName(@RequestBody CommanderValidateNameRequest request) {
        commanderService.validateCommanderName(request.getName());
        return ApiResponse.success(true);
    }

    // 커맨더 이름 변경 — JWT에서 commanderId 추출, 횟수 차감
    @PostMapping("/rename")
    public ApiResponse<CommanderRenameResponse> renameCommander(@RequestBody CommanderRenameRequest request, HttpServletRequest httpRequest) {
        Long actualCommanderId = getActualCommanderIdFromToken(httpRequest);
        CommanderRenameResponse response = commanderService.renameCommander(actualCommanderId, request);
        return ApiResponse.success(response);
    }

    // JWT 토큰에서 커맨더 ID 추출 (비트 마스킹 포함)
    private Long getActualCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_TOKEN);
        if (jwtUtil.hasCommanderId(token) == false) throw new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_INVALID_COMMANDER);
        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_INVALID_COMMANDER);
        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}


