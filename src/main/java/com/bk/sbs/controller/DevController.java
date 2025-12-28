package com.bk.sbs.controller;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.*;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.CharacterService;
import com.bk.sbs.service.FleetService;
import com.bk.sbs.service.GameDataService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

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

    private ApiResponse<String> executeDevCommand(String command, List<String> params, Long characterId) {
        switch (command.toLowerCase()) {
            case "setmineral":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long material = Long.parseLong(params.get(0));
                    characterService.updateMineral(characterId, material);
                    return ApiResponse.success("Mineral set to: " + material + "|mineral:" + material);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addmineral":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long additionalMaterial = Long.parseLong(params.get(0));
                    Long newMineral = characterService.addMineral(characterId, additionalMaterial);
                    return ApiResponse.success("Mineral added: " + additionalMaterial + " (total: " + newMineral + ")|mineral:" + newMineral);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "setmineralrare":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long mineralRare = Long.parseLong(params.get(0));
                    characterService.updateMineralRare(characterId, mineralRare);
                    return ApiResponse.success("Mineral Rare set to: " + mineralRare + "|mineralRare:" + mineralRare);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addmineralrare":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long additionalMineralRare = Long.parseLong(params.get(0));
                    Long newMineralRare = characterService.addMineralRare(characterId, additionalMineralRare);
                    return ApiResponse.success("Mineral Rare added: " + additionalMineralRare + " (total: " + newMineralRare + ")|mineralRare:" + newMineralRare);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "setmineralexotic":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long mineralExotic = Long.parseLong(params.get(0));
                    characterService.updateMineralExotic(characterId, mineralExotic);
                    return ApiResponse.success("Mineral Exotic set to: " + mineralExotic + "|mineralExotic:" + mineralExotic);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addmineralexotic":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long additionalMineralExotic = Long.parseLong(params.get(0));
                    Long newMineralExotic = characterService.addMineralExotic(characterId, additionalMineralExotic);
                    return ApiResponse.success("Mineral Exotic added: " + additionalMineralExotic + " (total: " + newMineralExotic + ")|mineralExotic:" + newMineralExotic);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "setmineraldark":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long mineralDark = Long.parseLong(params.get(0));
                    characterService.updateMineralDark(characterId, mineralDark);
                    return ApiResponse.success("Mineral Dark set to: " + mineralDark + "|mineralDark:" + mineralDark);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addmineraldark":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Long additionalMineralDark = Long.parseLong(params.get(0));
                    Long newMineralDark = characterService.addMineralDark(characterId, additionalMineralDark);
                    return ApiResponse.success("Mineral Dark added: " + additionalMineralDark + " (total: " + newMineralDark + ")|mineralDark:" + newMineralDark);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "addtech":
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    Integer additionalTech = Integer.parseInt(params.get(0));
                    Integer newTechLevel = characterService.addTechLevel(characterId, additionalTech);
                    return ApiResponse.success("Technology added: " + additionalTech + " (total: " + newTechLevel + ")|tech:" + newTechLevel);
                } catch (NumberFormatException e) {
                    return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                }

            case "getstatus":
                CharacterInfoDto status = characterService.getCharacterInfoDto(characterId);
                StringBuilder result = new StringBuilder();
                result.append("=== Character Status ===\n");
                result.append("Tech Level: ").append(status.getTechLevel());
                result.append("Mineral: ").append(status.getMineral()).append("\n");
                result.append("Mineral Rare: ").append(status.getMineralRare()).append("\n");
                result.append("Mineral Exotic: ").append(status.getMineralExotic()).append("\n");
                result.append("Mineral Dark: ").append(status.getMineralDark()).append("\n");                
                return ApiResponse.success(result.toString());

            case "addship":
                try {
                    // 개발자 명령어: 자원이 부족할 경우 자동으로 충원
                    CharacterInfoDto currentStatus = characterService.getCharacterInfoDto(characterId);

                    // 현재 함선 수 확인
                    FleetInfoDto activeFleet = fleetService.getActiveFleet(characterId);
                    int currentShipCount = activeFleet.getShips() != null ? activeFleet.getShips().size() : 0;

                    // 함선 추가에 필요한 자원 비용 확인 (GameDataService에서 가져오기)
                    CostStructDto shipAddCost = gameDataService.getShipAddCost(currentShipCount);

                    // 자원 부족 시 자동 충원 (모든 미네랄 타입)
                    if (currentStatus.getMineral() < shipAddCost.getMineral()) {
                        Long newMineral = currentStatus.getMineral() + shipAddCost.getMineral() + 5000;
                        characterService.updateMineral(characterId, newMineral);
                    }
                    if (currentStatus.getMineralRare() < shipAddCost.getMineralRare()) {
                        Long newMineralRare = currentStatus.getMineralRare() + shipAddCost.getMineralRare() + 1000;
                        characterService.updateMineralRare(characterId, newMineralRare);
                    }
                    if (currentStatus.getMineralExotic() < shipAddCost.getMineralExotic()) {
                        Long newMineralExotic = currentStatus.getMineralExotic() + shipAddCost.getMineralExotic() + 1000;
                        characterService.updateMineralExotic(characterId, newMineralExotic);
                    }
                    if (currentStatus.getMineralDark() < shipAddCost.getMineralDark()) {
                        Long newMineralDark = currentStatus.getMineralDark() + shipAddCost.getMineralDark() + 1000;
                        characterService.updateMineralDark(characterId, newMineralDark);
                    }

                    AddShipRequest addShipRequest = new AddShipRequest();
                    addShipRequest.setFleetId(null); // null이면 현재 활성 함대에 추가

                    AddShipResponse addShipResponse = fleetService.addShip(characterId, addShipRequest);

                    if (addShipResponse.getSuccess() == true) {
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
                if (params == null || params.isEmpty()) return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                try {
                    EFormationType formationType;

                    if (params.get(0).matches("\\d+")) {
                        int index = Integer.parseInt(params.get(0));
                        EFormationType[] formations = EFormationType.values();
                        if (index >= 0 && index < formations.length) {
                            formationType = formations[index];
                        } else {
                            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                        }
                    } else {
                        try {
                            formationType = EFormationType.valueOf(params.get(0));
                        } catch (IllegalArgumentException e) {
                            return ApiResponse.error(ServerErrorCode.UNKNOWN_ERROR);
                        }
                    }

                    ChangeFormationRequest changeFormationRequest = new ChangeFormationRequest();
                    changeFormationRequest.setFleetId(null);
                    changeFormationRequest.setFormationType(formationType);

                    ChangeFormationResponse changeFormationResponse = fleetService.changeFormation(characterId, changeFormationRequest);

                    if (changeFormationResponse.getSuccess() == true) {
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