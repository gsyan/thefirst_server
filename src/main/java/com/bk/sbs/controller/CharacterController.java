//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.AuthResponse;
import com.bk.sbs.dto.CharacterCreateRequest;
import com.bk.sbs.dto.CharacterRenameRequest;
import com.bk.sbs.dto.CharacterRenameResponse;
import com.bk.sbs.dto.CharacterValidateNameRequest;
import com.bk.sbs.dto.CharacterResponse;
import com.bk.sbs.dto.CharacterInfoDto;
import com.bk.sbs.dto.FleetInfoDto;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.AccountService;
import com.bk.sbs.service.CharacterService;
import com.bk.sbs.service.FleetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/character")
public class CharacterController {

    private final AccountService accountService;
    private final CharacterService characterService;
    private final FleetService fleetService;
    private final JwtUtil jwtUtil;

    public CharacterController(AccountService accountService, CharacterService characterService, FleetService fleetService, JwtUtil jwtUtil) {
        this.accountService = accountService;
        this.characterService = characterService;
        this.fleetService = fleetService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/create")
    public ApiResponse<CharacterResponse> createCharacter(@RequestBody CharacterCreateRequest request) {
        CharacterResponse response = characterService.createCharacter(request);
        return ApiResponse.success(response);
    }


    // 캐릭터 선택 후 토큰 갱신
    @PostMapping("/select-character/{characterId}")
    public ApiResponse<AuthResponse> selectCharacter(@PathVariable("characterId") Long characterId, HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_NULL_TOKEN);
        Long accountId = jwtUtil.getAccountIdFromSubject(token);
        if (accountId == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_NULL_ACCOUNTID);
        // characterId에서 실제 character ID 추출 (하위 56비트)
        Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;

        // 캐릭터가 해당 계정에 속하는지 확인
        boolean isValidCharacter = accountService.validateCharacterOwnership(accountId, actualCharacterId);
        if (isValidCharacter == false) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_INVALID_CHARACTER);

        // 새로운 토큰 생성 (characterId 포함)
        String newAccessToken = jwtUtil.createAccessTokenWithCharacter(accountId, characterId);
        String newRefreshToken = jwtUtil.createRefreshTokenWithCharacter(accountId, characterId);

        // collectDateTime 12h 캡 적용 + 마지막 온라인 시간 갱신
        characterService.applyOfflineCapAndUpdateLastOnline(actualCharacterId);

        // 캐릭터의 활성 함대 정보 조회
        FleetInfoDto activeFleet = fleetService.getActiveFleet(actualCharacterId);

        // 활성 함대가 없다면 null로 반환 (캐릭터 생성 시 기본 함대가 생성되어야 함)
        // 정상적인 경우라면 이미 기본 함대가 존재해야 함
        if (activeFleet == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET);

        // 캐릭터 상태 정보 조회
        CharacterInfoDto characterInfoDto = characterService.getCharacterInfoDto(actualCharacterId);

        // 개발된 모듈 목록 조회
        var researchedModuleTypes = fleetService.getResearchedModuleTypes(actualCharacterId);

        boolean bGoogleLinked = accountService.isGoogleLinked(accountId);

        AuthResponse response = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .activeFleetInfo(activeFleet)
                .characterInfo(characterInfoDto)
                .researchedModuleTypes(researchedModuleTypes)
                .bGoogleLinked(bGoogleLinked)
                .build();
        return ApiResponse.success(response);
    }

    @GetMapping("/characters")
    public ApiResponse<List<CharacterResponse>> getAllCharacters() {
        return accountService.getAllCharacters();
    }

    // 이름 유효성 검사 (실시간 입력 중 호출) — 중복·비속어만 검사, 포맷은 클라에서 처리
    @PostMapping("/validate-name")
    public ApiResponse<Boolean> validateCharacterName(@RequestBody CharacterValidateNameRequest request) {
        characterService.validateCharacterName(request.getName());
        return ApiResponse.success(true);
    }

    // 캐릭터 이름 변경 — JWT에서 characterId 추출, 횟수 차감
    @PostMapping("/rename")
    public ApiResponse<CharacterRenameResponse> renameCharacter(@RequestBody CharacterRenameRequest request, HttpServletRequest httpRequest) {
        Long actualCharacterId = getActualCharacterIdFromToken(httpRequest);
        CharacterRenameResponse response = characterService.renameCharacter(actualCharacterId, request);
        return ApiResponse.success(response);
    }

    // JWT 토큰에서 캐릭터 ID 추출 (비트 마스킹 포함)
    private Long getActualCharacterIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_NULL_TOKEN);
        if (jwtUtil.hasCharacterId(token) == false) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_INVALID_CHARACTER);
        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_INVALID_CHARACTER);
        return characterId & 0x00FFFFFFFFFFFFFFL;
    }
}
