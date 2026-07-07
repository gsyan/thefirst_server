package com.bk.sbs.controller;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.*;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.entity.PvpSeason;
import com.bk.sbs.service.CommanderService;
import com.bk.sbs.service.FleetService;
import com.bk.sbs.service.GameDataService;
import com.bk.sbs.service.PvpSeasonService;
import com.bk.sbs.service.ZoneService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final CommanderService CommanderService;
    private final FleetService fleetService;
    private final GameDataService gameDataService;
    private final PvpSeasonService pvpSeasonService;
    private final ZoneService zoneService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public DevController(CommanderService CommanderService, FleetService fleetService, GameDataService gameDataService, PvpSeasonService pvpSeasonService, ZoneService zoneService, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.CommanderService = CommanderService;
        this.fleetService = fleetService;
        this.gameDataService = gameDataService;
        this.pvpSeasonService = pvpSeasonService;
        this.zoneService = zoneService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/command")
    public ApiResponse<String> executeCommand(@RequestBody DevCommandRequest request, HttpServletRequest httpRequest) {
        String token = jwtUtil.getTokenFromRequest(httpRequest);
        if (token == null) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_NULL_TOKEN);
        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_NULL_COMMANDERID);
        Long actualCommanderId = commanderId & 0x00FFFFFFFFFFFFFFL;
        return executeDevCommand(request.getCommand(), request.getParams(), actualCommanderId);
    }

    private ApiResponse<String> executeDevCommand(String command, List<String> params, Long commanderId) {
        switch (command.toLowerCase()) {
            case "setmineral":
                if (params == null || params.isEmpty()) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_SETMINERAL_INVALID_PARAM);
                int mineral = parseIntOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_SETMINERAL_PARSE_PARAM);
                CommanderService.updateMineral(commanderId, mineral);
                return ApiResponse.success("Mineral set to: " + mineral + "|mineral:" + mineral);
            case "addmineral":
                if (params == null || params.isEmpty()) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMIKNERAL_INVALID_PARAM);
                int additionalMaterial = parseIntOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMIKNERAL_PARSE_PARAM);
                int newMineral = CommanderService.addMineral(commanderId, additionalMaterial);
                return ApiResponse.success("Mineral added: " + additionalMaterial + " (total: " + newMineral + ")|mineral:" + newMineral);

            case "addminerals": {
                // params: [mineral] [levelUp] [modulePoint] [pvpPoint] — 0이면 해당 타입 스킵, levelUp>0이면 정확히 1레벨만 증가
                if (params == null || params.size() < 4) throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMINERALS_INVALID_PARAM);
                int addM      = parseIntOrThrow(params.get(0), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDMIKNERAL_PARSE_PARAM);
                int addLevel  = parseIntOrThrow(params.get(1), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDPVPMINERAL_PARSE_PARAM);
                int addMp     = parseIntOrThrow(params.get(2), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDTEMPMINERAL_PARSE_PARAM);
                int addPvp    = parseIntOrThrow(params.get(3), ServerErrorCode.EXECUTE_COMMAND_FAIL_ADDPVPMINERAL_PARSE_PARAM);
                CommanderInfoDto cur = CommanderService.getCommanderInfoDto(commanderId);
                int newM      = addM     > 0 ? CommanderService.addMineral(commanderId, addM)               : cur.getMineral();
                int newMpMax  = addMp    > 0 ? CommanderService.addModulePointMaxGot(commanderId, addMp)    : cur.getModulePointMaxGot();
                int newMp     = addMp    > 0 ? CommanderService.addModulePoint(commanderId, addMp)          : cur.getModulePoint();
                int newPvpMax = addPvp   > 0 ? CommanderService.addPvpPointMaxGot(commanderId, addPvp)      : cur.getPvpPointMaxGot();
                int newPvp    = addPvp   > 0 ? CommanderService.addPvpPoint(commanderId, addPvp)            : cur.getPvpPoint();
                int newCommanderLevel = addLevel > 0 ? zoneService.addOneCommanderLevel(commanderId) : cur.getCommanderLevel();
                int newExp    = addLevel > 0 ? CommanderService.getCommanderInfoDto(commanderId).getExp()  : cur.getExp();
                return ApiResponse.success("Resources added|mineral:" + newM + "|exp:" + newExp + "|modulePointMaxGot:" + newMpMax + "|modulePoint:" + newMp + "|pvpPointMaxGot:" + newPvpMax + "|pvpPoint:" + newPvp + "|commanderLevel:" + newCommanderLevel);
            }

            case "getstatus":
                CommanderInfoDto status = CommanderService.getCommanderInfoDto(commanderId);
                StringBuilder result = new StringBuilder();
                result.append("=== Commander Status ===\n");
                result.append("Mineral: ").append(status.getMineral()).append("\n");
                return ApiResponse.success(result.toString());

//            case "addship":
//                // 개발자 명령어: 자원이 부족할 경우 자동으로 충원
//                CommanderInfoDto currentStatus = CommanderService.getCommanderInfoDto(commanderId);
//
//                // 현재 함선 수 확인
//                FleetInfoDto activeFleet = fleetService.getActiveFleet(commanderId);
//                int currentShipCount = activeFleet.getShips() != null ? activeFleet.getShips().size() : 0;
//
//                // 함선 추가에 필요한 자원 비용 확인 (GameDataService에서 가져오기)
//                CostStructDto shipAddCost = gameDataService.getShipAddCost(currentShipCount);
//
//                // 자원 부족 시 자동 충원 (모든 미네랄 타입)
//                if (currentStatus.getMineral() < shipAddCost.getMineral()) {
//                    Long updatedMineral = currentStatus.getMineral() + shipAddCost.getMineral() + 5000;
//                    CommanderService.updateMineral(commanderId, updatedMineral);
//                }
//                if (currentStatus.getMineralRare() < shipAddCost.getMineralRare()) {
//                    Long updatedMineralRare = currentStatus.getMineralRare() + shipAddCost.getMineralRare() + 1000;
//                    CommanderService.updateMineralRare(commanderId, updatedMineralRare);
//                }
//                if (currentStatus.getMineralExotic() < shipAddCost.getMineralExotic()) {
//                    Long updatedMineralExotic = currentStatus.getMineralExotic() + shipAddCost.getMineralExotic() + 1000;
//                    CommanderService.updateMineralExotic(commanderId, updatedMineralExotic);
//                }
//                if (currentStatus.getMineralDark() < shipAddCost.getMineralDark()) {
//                    Long updatedMineralDark = currentStatus.getMineralDark() + shipAddCost.getMineralDark() + 1000;
//                    CommanderService.updateMineralDark(commanderId, updatedMineralDark);
//                }
//
//                AddShipRequest addShipRequest = new AddShipRequest();
//                addShipRequest.setFleetId(null); // null이면 현재 활성 함대에 추가
//
//                AddShipResponse addShipResponse = fleetService.addShip(commanderId, addShipRequest);
//                String addShipJson = jsonSerializeOrThrow(addShipResponse);
//                return ApiResponse.success(addShipJson);

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

                ChangeFormationResponse changeFormationResponse = fleetService.changeFormation(commanderId, changeFormationRequest);
                String changeFormationJson = jsonSerializeOrThrow(changeFormationResponse);
                return ApiResponse.success(changeFormationJson);

            // pvpseason set [시즌번호] [시작ISO] [종료ISO]
            // 예) pvpseason set 1 2026-05-01T00:00:00Z 2026-05-15T00:00:00Z
            case "pvpseason": {
                if (params == null || params.size() < 1)
                    throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_UNKNOWN_COMMAND);
                String subCmd = params.get(0).toLowerCase();

                if (subCmd.equals("set")) {
                    // params: [set, seasonNumber, startISO, endISO]
                    if (params.size() < 4)
                        throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_UNKNOWN_COMMAND);
                    int seasonNumber = parseIntOrThrow(params.get(1), ServerErrorCode.EXECUTE_COMMAND_FAIL_UNKNOWN_COMMAND);
                    java.time.Instant startTime = java.time.Instant.parse(params.get(2));
                    java.time.Instant endTime   = java.time.Instant.parse(params.get(3));
                    PvpSeason season = pvpSeasonService.setSeasonManual(seasonNumber, startTime, endTime);
                    return ApiResponse.success("시즌 설정 완료|season:" + season.getSeasonNumber()
                            + "|end:" + season.getEndTime());

                } else if (subCmd.equals("end")) {
                    // 현재 시즌 즉시 종료 → 보상 지급 + 점수 리셋 + 다음 시즌 자동 시작
                    return pvpSeasonService.getCurrentSeason()
                            .map(season -> {
                                pvpSeasonService.endSeasonAndStartNext(season);
                                return ApiResponse.success("시즌 " + season.getSeasonNumber() + " 종료 처리 완료");
                            })
                            .orElse(ApiResponse.success("진행 중인 시즌 없음"));

                } else if (subCmd.equals("distribute")) {
                    // 보상만 재지급 (테스트용, rewardDistributed 무시)
                    return pvpSeasonService.getCurrentSeason()
                            .map(season -> {
                                season.setRewardDistributed(false);
                                pvpSeasonService.distributeSeasonReward(season);
                                return ApiResponse.success("시즌 " + season.getSeasonNumber() + " 보상 재지급 완료");
                            })
                            .orElse(ApiResponse.success("진행 중인 시즌 없음"));

                } else if (subCmd.equals("reset")) {
                    // 점수만 리셋 (테스트용)
                    pvpSeasonService.resetSeasonScores();
                    return ApiResponse.success("시즌 점수 리셋 완료");
                }

                throw new BusinessException(ServerErrorCode.EXECUTE_COMMAND_FAIL_UNKNOWN_COMMAND);
            }

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




