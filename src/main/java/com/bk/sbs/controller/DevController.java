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
        String token = jwtUtil.getTokenFromRequest(httpRequest);
        if (token == null) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_NULL_TOKEN);
        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_NULL_CHARACTERID);
        Long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
        return executeDevCommand(request.getCommand(), request.getParams(), actualCharacterId);
    }

    private ApiResponse<String> executeDevCommand(String command, List<String> params, Long characterId) {
        switch (command.toLowerCase()) {
            case "setmineral":
                if (params == null || params.isEmpty()) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_SETMINERAL_INVALID_PARAM);
                Long mineral = parseOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_SETMINERAL_PARSE_PARAM);
                characterService.updateMineral(characterId, mineral);
                return ApiResponse.success("Mineral set to: " + mineral + "|mineral:" + mineral);
            case "addmineral":
                if (params == null || params.isEmpty()) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMIKNERAL_INVALID_PARAM);
                Long additionalMaterial = parseOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMIKNERAL_PARSE_PARAM);
                Long newMineral = characterService.addMineral(characterId, additionalMaterial);
                return ApiResponse.success("Mineral added: " + additionalMaterial + " (total: " + newMineral + ")|mineral:" + newMineral);

            // addmineral/rare/exotic/dark 를 한 번에 처리 (0이면 해당 타입 스킵)
            // usage: addminerals [mineral] [mineralRare] [mineralExotic] [mineralDark]
            case "addminerals":
                if (params == null || params.size() < 4) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMIKNERAL_INVALID_PARAM);
                Long amtMineral      = parseOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMIKNERAL_PARSE_PARAM);
                Long amtMineralRare  = parseOrThrow(params.get(1), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMINERALRARE_PARSE_PARAM);
                Long amtMineralExotic = parseOrThrow(params.get(2), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMINERALEXOTIC_PARSE_PARAM);
                Long amtMineralDark  = parseOrThrow(params.get(3), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMINERALDARK_PARSE_PARAM);
                if (amtMineral > 0)       characterService.addMineral(characterId, amtMineral);
                if (amtMineralRare > 0)   characterService.addMineralRare(characterId, amtMineralRare);
                if (amtMineralExotic > 0) characterService.addMineralExotic(characterId, amtMineralExotic);
                if (amtMineralDark > 0)   characterService.addMineralDark(characterId, amtMineralDark);
                CharacterInfoDto updated = characterService.getCharacterInfoDto(characterId);
                return ApiResponse.success("Minerals added"
                    + "|mineral:" + updated.getMineral()
                    + "|mineralRare:" + updated.getMineralRare()
                    + "|mineralExotic:" + updated.getMineralExotic()
                    + "|mineralDark:" + updated.getMineralDark());

            case "addtech":
                if (params == null || params.isEmpty()) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDTECH_INVALID_PARAM);
                Integer additionalTech = parseIntOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDTECH_PARSE_PARAM);
                Integer newTechLevel = characterService.addTechLevel(characterId, additionalTech);
                return ApiResponse.success("Technology added: " + additionalTech + " (total: " + newTechLevel + ")|tech:" + newTechLevel);

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
                // 개발자 명령어: 자원이 부족할 경우 자동으로 충원
                CharacterInfoDto currentStatus = characterService.getCharacterInfoDto(characterId);

                // 현재 함선 수 확인
                FleetInfoDto activeFleet = fleetService.getActiveFleet(characterId);
                int currentShipCount = activeFleet.getShips() != null ? activeFleet.getShips().size() : 0;

                // 함선 추가에 필요한 자원 비용 확인 (GameDataService에서 가져오기)
                CostStructDto shipAddCost = gameDataService.getShipAddCost(currentShipCount);

                // 자원 부족 시 자동 충원 (모든 미네랄 타입)
                if (currentStatus.getMineral() < shipAddCost.getMineral()) {
                    Long updatedMineral = currentStatus.getMineral() + shipAddCost.getMineral() + 5000;
                    characterService.updateMineral(characterId, updatedMineral);
                }
                if (currentStatus.getMineralRare() < shipAddCost.getMineralRare()) {
                    Long updatedMineralRare = currentStatus.getMineralRare() + shipAddCost.getMineralRare() + 1000;
                    characterService.updateMineralRare(characterId, updatedMineralRare);
                }
                if (currentStatus.getMineralExotic() < shipAddCost.getMineralExotic()) {
                    Long updatedMineralExotic = currentStatus.getMineralExotic() + shipAddCost.getMineralExotic() + 1000;
                    characterService.updateMineralExotic(characterId, updatedMineralExotic);
                }
                if (currentStatus.getMineralDark() < shipAddCost.getMineralDark()) {
                    Long updatedMineralDark = currentStatus.getMineralDark() + shipAddCost.getMineralDark() + 1000;
                    characterService.updateMineralDark(characterId, updatedMineralDark);
                }

                AddShipRequest addShipRequest = new AddShipRequest();
                addShipRequest.setFleetId(null); // null이면 현재 활성 함대에 추가

                AddShipResponse addShipResponse = fleetService.addShip(characterId, addShipRequest);
                String addShipJson = jsonSerializeOrThrow(addShipResponse);
                return ApiResponse.success(addShipJson);

            case "changeformation":
                if (params == null || params.isEmpty()) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_CHANGEFORMATION_INVALID_PARAM);

                EFormationType formationType;
                if (params.get(0).matches("\\d+")) {
                    int index = parseIntOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_CHANGEFORMATION_PARSE_PARAM);
                    EFormationType[] formations = EFormationType.values();
                    if (index >= 0 && index < formations.length) {
                        formationType = formations[index];
                    } else {
                        throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_CHANGEFORMATION_INVALID_INDEX);
                    }
                } else {
                    try {
                        formationType = EFormationType.valueOf(params.get(0));
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_CHANGEFORMATION_INVALID_TYPE);
                    }
                }

                ChangeFormationRequest changeFormationRequest = new ChangeFormationRequest();
                changeFormationRequest.setFleetId(null);
                changeFormationRequest.setFormationType(formationType);

                ChangeFormationResponse changeFormationResponse = fleetService.changeFormation(characterId, changeFormationRequest);
                String changeFormationJson = jsonSerializeOrThrow(changeFormationResponse);
                return ApiResponse.success(changeFormationJson);

            default:
                throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_UNKNOWN_COMMAND);
        }
    }

    private Long parseOrThrow(String value, ServerErrorCode errorCode) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(errorCode);
        }
    }

    private Integer parseIntOrThrow(String value, ServerErrorCode errorCode) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(errorCode);
        }
    }

    private String jsonSerializeOrThrow(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_JSON_SERIALIZE);
        }
    }

}