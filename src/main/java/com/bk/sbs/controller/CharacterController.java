//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.AuthResponse;
import com.bk.sbs.dto.CharacterCreateRequest;
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
        String email = jwtUtil.getEmailFromToken(token);
        if (email == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_NULL_EMAIL);
        Long accountId = jwtUtil.getAccountIdFromToken(token);
        if (accountId == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_NULL_ACCOUNTID);
        // characterId에서 실제 character ID 추출 (하위 56비트)
        Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;

        // 캐릭터가 해당 계정에 속하는지 확인
        boolean isValidCharacter = accountService.validateCharacterOwnership(accountId, actualCharacterId);
        if (isValidCharacter == false) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_INVALID_CHARACTER);

        // 새로운 토큰 생성 (characterId 포함)
        String newAccessToken = jwtUtil.createAccessTokenWithCharacter(email, accountId, characterId);
        String newRefreshToken = jwtUtil.createRefreshTokenWithCharacter(email, accountId, characterId);

        // 캐릭터의 활성 함대 정보 조회
        FleetInfoDto activeFleet = fleetService.getActiveFleet(actualCharacterId);

        // 활성 함대가 없다면 null로 반환 (캐릭터 생성 시 기본 함대가 생성되어야 함)
        // 정상적인 경우라면 이미 기본 함대가 존재해야 함
        if (activeFleet == null) throw new BusinessException(ServerErrorCode.CHARACTER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET);

        // 캐릭터 상태 정보 조회
        CharacterInfoDto characterInfoDto = characterService.getCharacterInfoDto(actualCharacterId);

        // 개발된 모듈 목록 조회
        var researchedModuleTypes = fleetService.getResearchedModuleTypePackeds(actualCharacterId);

        AuthResponse response = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .activeFleetInfo(activeFleet)
                .characterInfo(characterInfoDto)
                .researchedModuleTypes(researchedModuleTypes)
                .build();
        return ApiResponse.success(response);
    }

    @GetMapping("/characters")
    public ApiResponse<List<CharacterResponse>> getAllCharacters() {
        return accountService.getAllCharacters();
    }
}
