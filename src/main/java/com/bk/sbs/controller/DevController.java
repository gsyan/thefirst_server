package com.bk.sbs.controller;

import com.bk.sbs.dto.ApiResponse;
import com.bk.sbs.dto.CharacterStatusResponse;
import com.bk.sbs.dto.DevCommandRequest;
import com.bk.sbs.dto.AddShipRequest;
import com.bk.sbs.dto.AddShipResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.CharacterService;
import com.bk.sbs.service.FleetService;
import com.bk.sbs.service.GameDataService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final CharacterService characterService;
    private final FleetService fleetService;
    private final GameDataService gameDataService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public DevController(CharacterService characterService, FleetService fleetService, GameDataService gameDataService, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.characterService = characterService;
        this.fleetService = fleetService;
        this.gameDataService = gameDataService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/command")
    public ApiResponse<String> executeCommand(@RequestBody DevCommandRequest request, HttpServletRequest httpRequest) {
        try {
            String token = jwtUtil.getTokenFromRequest(httpRequest);
            if (token == null) return ApiResponse.error(ServerErrorCode.LOGIN_FAIL_REASON1);

            Long characterId = jwtUtil.getCharacterIdFromToken(token);
            if (characterId == null) return ApiResponse.error(ServerErrorCode.LOGIN_FAIL_REASON1);

            Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;

            return executeDevCommand(request.getCommand(), request.getParams(), actualCharacterId);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getErrorCode());
        } catch (Exception e) {
            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
        }
    }

    private ApiResponse<String> executeDevCommand(String command, String[] params, Long characterId) {
        switch (command.toLowerCase()) {
            case "setmoney":
                if (params == null || params.length == 0) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long money = Long.parseLong(params[0]);
                    characterService.updateMoney(characterId, money);
                    return ApiResponse.success("Money set to: " + money + "|money:" + money);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addmoney":
                if (params == null || params.length == 0) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long additionalMoney = Long.parseLong(params[0]);
                    long newMoney = characterService.addMoney(characterId, additionalMoney);
                    return ApiResponse.success("Money added: " + additionalMoney + " (total: " + newMoney + ")|money:" + newMoney);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "setmineral":
                if (params == null || params.length == 0) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long material = Long.parseLong(params[0]);
                    characterService.updateMineral(characterId, material);
                    return ApiResponse.success("Mineral set to: " + material + "|mineral:" + material);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addmineral":
                if (params == null || params.length == 0) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long additionalMaterial = Long.parseLong(params[0]);
                    Long newMineral = characterService.addMineral(characterId, additionalMaterial);
                    return ApiResponse.success("Mineral added: " + additionalMaterial + " (total: " + newMineral + ")|mineral:" + newMineral);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addtech":
                if (params == null || params.length == 0) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Integer additionalTech = Integer.parseInt(params[0]);
                    Integer newTechLevel = characterService.addTechLevel(characterId, additionalTech);
                    return ApiResponse.success("Technology added: " + additionalTech + " (total: " + newTechLevel + ")|tech:" + newTechLevel);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "getstatus":
                CharacterStatusResponse status = characterService.getCharacterStatus(characterId);
                StringBuilder result = new StringBuilder();
                result.append("=== Character Status ===\n");
                result.append("Money: ").append(status.getMoney()).append("\n");
                result.append("Mineral: ").append(status.getMineral()).append("\n");
                result.append("Technology Level: ").append(status.getTechnologyLevel());
                return ApiResponse.success(result.toString());

            case "addship":
                try {
                    // 개발자 명령어: 자원이 부족할 경우 자동으로 충원
                    CharacterStatusResponse currentStatus = characterService.getCharacterStatus(characterId);

                    // 함선 추가에 필요한 자원 비용 확인 (GameDataService에서 가져오기)
                    int shipAddMoneyCost = gameDataService.getShipAddMoneyCost();
                    int shipAddMineralCost = gameDataService.getShipAddMineralCost();

                    // 자원 부족 시 자동 충원
                    Long currentMoney = currentStatus.getMoney();
                    Long currentMineral = currentStatus.getMineral();

                    if (currentMoney < shipAddMoneyCost) {
                        Long newMoney = currentMoney + shipAddMoneyCost + 10000; // 여유분 추가
                        characterService.updateMoney(characterId, newMoney);
                    }

                    if (currentMineral < shipAddMineralCost) {
                        Long newMineral = currentMineral + shipAddMineralCost + 5000; // 여유분 추가
                        characterService.updateMineral(characterId, newMineral);
                    }

                    AddShipRequest addShipRequest = new AddShipRequest();
                    addShipRequest.setFleetId(null); // null이면 현재 활성 함대에 추가

                    AddShipResponse addShipResponse = fleetService.addShip(characterId, addShipRequest);

                    if (addShipResponse.isSuccess()) {
                        try {
                            String jsonResponse = objectMapper.writeValueAsString(addShipResponse);
                            return ApiResponse.success(jsonResponse);
                        } catch (Exception e) {
                            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                        }
                    } else {
                        return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                    }
                } catch (BusinessException e) {
                    return ApiResponse.error(e.getErrorCode());
                } catch (Exception e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "changeformation":
                if (params == null || params.length == 0) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    com.bk.sbs.enums.EFormationType formationType;

                    if (params[0].matches("\\d+")) {
                        int index = Integer.parseInt(params[0]);
                        com.bk.sbs.enums.EFormationType[] formations = com.bk.sbs.enums.EFormationType.values();
                        if (index >= 0 && index < formations.length) {
                            formationType = formations[index];
                        } else {
                            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                        }
                    } else {
                        try {
                            formationType = com.bk.sbs.enums.EFormationType.valueOf(params[0]);
                        } catch (IllegalArgumentException e) {
                            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                        }
                    }

                    com.bk.sbs.dto.ChangeFormationRequest changeFormationRequest = new com.bk.sbs.dto.ChangeFormationRequest();
                    changeFormationRequest.setFleetId(null);
                    changeFormationRequest.setFormationType(formationType);

                    com.bk.sbs.dto.ChangeFormationResponse changeFormationResponse = fleetService.changeFormation(characterId, changeFormationRequest);

                    if (changeFormationResponse.isSuccess()) {
                        try {
                            String jsonResponse = objectMapper.writeValueAsString(changeFormationResponse);
                            return ApiResponse.success(jsonResponse);
                        } catch (Exception e) {
                            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                        }
                    } else {
                        return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                    }
                } catch (BusinessException e) {
                    return ApiResponse.error(e.getErrorCode());
                } catch (Exception e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            default:
                return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
        }
    }
}