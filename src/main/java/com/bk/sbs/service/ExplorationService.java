// 탐사 그리드 존 진행(ZoneRun) 서비스 — 셀 입장/클리어/탈출/포기, 지휘관 경험치 정산, 지휘력 최대치 구매
package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderZoneFullClear;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.entity.ZoneCellClearLog;
import com.bk.sbs.entity.ZoneRun;
import com.bk.sbs.enums.EAchievementConditionType;
import com.bk.sbs.enums.EGridCellType;
import com.bk.sbs.enums.EGridEventType;
import com.bk.sbs.enums.ETreasureRewardType;
import com.bk.sbs.enums.EZoneRunStatus;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.CommanderZoneFullClearRepository;
import com.bk.sbs.repository.VipSubscriptionRepository;
import com.bk.sbs.repository.ZoneCellClearLogRepository;
import com.bk.sbs.repository.ZoneRunRepository;
import com.bk.sbs.util.CommanderLevelUtil;
import com.bk.sbs.util.RewardCardSelector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

@Service
@Slf4j
public class ExplorationService {

    private final CommanderRepository commanderRepository;
    private final ZoneRunRepository zoneRunRepository;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final GameDataService gameDataService;
    private final ObjectMapper objectMapper;
    private final AchievementService achievementService;
    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final RedisService redisService; // 첫 셀(ZoneRun 생성 전) 클리어 챌린지 임시 저장용
    private final CommanderZoneFullClearRepository commanderZoneFullClearRepository;
    private final DailyCounterService dailyCounterService;
    private final FleetService fleetService;
    private final HullArchetypeProgressService hullArchetypeProgressService;

    // 아키타입 패시브 해금 카운터 이름 — DataTableHullArchetypePassive.counter_type과 동일해야 함
    private static final String k_counterDestroyEnemyShip = "destroy_enemy_ship";
    private static final String k_counterScavengeMineral = "scavenge_mineral";
    private static final String k_counterFleetResourceSaved = "fleet_resource_saved";
    // 클라가 아키타입별로 보고하는 카운터 — 이 목록에 없는 종류는 무시. 함선 자신이 겪은 값(받은 데미지 등) +
    // 함대 범위 값(적 격침/약탈 광물/절감 자원: 이벤트 순간 살아있던 함선의 아키타입에만 적립)
    private static final Set<String> k_reportableArchetypeCounters = Set.of("damage_taken_cumulative", "shield_absorb_amount", "intercept_count", "scout_success", "ally_repair_amount", "ally_shield_charge_amount",
            k_counterDestroyEnemyShip, k_counterScavengeMineral, k_counterFleetResourceSaved);
    private static final String k_counterZoneRunClear = "zone_run_clear";

    // 클라가 셀 클리어 때 보고하는 전투 통계의 셀 1회당 상한(비정상 값 보정)
    private static final int k_maxReportedKillsPerCell = 100;
    private static final int k_maxReportedLaunchesPerCell = 500;

    // false면 highestClearedZoneNumber 검사를 건너뜀 — 웨이브 밸런스 테스트용(application.properties)
    @Value("${zone.require-previous-stage-cleared:true}")
    private boolean requirePreviousStageCleared;

    public ExplorationService(CommanderRepository commanderRepository, ZoneRunRepository zoneRunRepository,
                               ZoneCellClearLogRepository zoneCellClearLogRepository,
                               GameDataService gameDataService, ObjectMapper objectMapper,
                               AchievementService achievementService,
                               VipSubscriptionRepository vipSubscriptionRepository,
                               RedisService redisService,
                               CommanderZoneFullClearRepository commanderZoneFullClearRepository,
                               DailyCounterService dailyCounterService,
                               FleetService fleetService,
                               HullArchetypeProgressService hullArchetypeProgressService) {
        this.fleetService = fleetService;
        this.hullArchetypeProgressService = hullArchetypeProgressService;
        this.commanderRepository = commanderRepository;
        this.zoneRunRepository = zoneRunRepository;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.gameDataService = gameDataService;
        this.objectMapper = objectMapper;
        this.achievementService = achievementService;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.redisService = redisService;
        this.commanderZoneFullClearRepository = commanderZoneFullClearRepository;
        this.dailyCounterService = dailyCounterService;
    }

    // 런 시작 시점의 활성 함대 아키타입을 "처음부터 계속 함대에 있던 아키타입"으로 기록 — 이후 함대가 바뀌면 FleetService가 교집합으로 줄임
    private void recordRunStartArchetypes(ZoneRun run, Long commanderId) {
        Set<String> archetypes = fleetService.getActiveFleetArchetypes(commanderId);
        run.setContinuousArchetypes(String.join(",", archetypes));
    }

    // 활성 VIP 여부 — IapService.isVipActive()와 동일 기준(서비스 간 커플링 없이 각자 보유)
    private boolean isVipActive(Long commanderId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCommanderId(commanderId);
        Instant expiry = sub.isPresent() ? sub.get().getVipExpiry() : null;
        return expiry != null && Instant.now().isBefore(expiry);
    }

    // 셀 클리어 요청에 실린 함대 체력 스냅샷을 JSON으로 직렬화 — 비어있으면(null/빈 리스트) 기존 저장값을 그대로 둠(스냅샷 없이 보낸 요청이 덮어쓰지 않도록)
    private String serializeHealthSnapshot(List<ShipHealthRatioInfoDto> shipHealthRatios, String previousJson) {
        if (shipHealthRatios == null || shipHealthRatios.isEmpty()) return previousJson;
        try {
            return objectMapper.writeValueAsString(shipHealthRatios);
        } catch (Exception e) {
            log.error("Failed to serialize fleet health snapshot", e);
            return previousJson;
        }
    }

    private List<ShipHealthRatioInfoDto> deserializeHealthSnapshot(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<ShipHealthRatioInfoDto>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize fleet health snapshot", e);
            return null;
        }
    }

    private static final float HEALTH_RATIO_MIN = 0f;
    private static final float HEALTH_RATIO_MAX = 1f;

    // Treasure(Event) 셀 무료 보상 — 3종을 모두 선택지로 보여주고 유저가 1개 선택
    private static final int TREASURE_MINERAL_MULTIPLIER = 3; // 그 존의 일반 전투 셀 보상의 3배
    private static final float TREASURE_SHIP_HEALTH_HEAL_RATIO = 0.5f;
    private static final float TREASURE_RESOURCE_CHARGE_RATIO = 0.2f;


    // 체력 비율 범위(0~1)만 검증 — 피닉스 아군 수리 등 전투 중 회복량이 커서 직전 스냅샷 대비 증가폭은 검사하지 않음
    private void validateHealthSnapshot(List<ShipHealthRatioInfoDto> reported) {
        if (reported == null || reported.isEmpty()) return;

        for (ShipHealthRatioInfoDto info : reported) {
            if (info.getHealthRatio() == null) continue;
            if (info.getHealthRatio() < HEALTH_RATIO_MIN || info.getHealthRatio() > HEALTH_RATIO_MAX)
                throw new BusinessException(ServerErrorCode.EXPLORATION_FLEET_HEALTH_INVALID);
        }
    }

    // cardId 리스트 JSON 직렬화/역직렬화 — 셀 클리어 로그의 후보 3개, 재구성된 선택 이력 등 공용으로 사용
    private String serializeCardIdList(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(cardIds);
        } catch (Exception e) {
            log.error("Failed to serialize reward card id list", e);
            return null;
        }
    }

    private List<String> deserializeCardIdList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize reward card id list", e);
            return null;
        }
    }

    // 가중치 랜덤으로 후보 3개 추첨 — 결과는 ZoneRun.rewardCardCandidatesJson에 그대로 저장되므로 재계산할 필요 없음(구매/리롤 검증도 저장값 기준)
    // excludeCardIds: 지금 보여주고 있던 카드ID들 — 같은 효과 종류는 다시 나올 수 있지만 정확히 같은 등급(카드)은 이번엔 배제
    private List<String> rollRewardCardCandidates(List<String> excludeCardIds) {
        List<GameDataService.RewardCardEntry> pool = gameDataService.getRewardCardList();
        if (pool.isEmpty()) return null;

        List<GameDataService.RewardCardEntry> picked = RewardCardSelector.selectCandidates(pool, 3, new java.util.Random(), excludeCardIds);

        List<String> cardIds = new ArrayList<>();
        for (GameDataService.RewardCardEntry entry : picked) cardIds.add(entry.cardId);
        // [디버그] 특정 보상 카드 테스트용 강제 노출 — 필요할 때만 주석 해제 (카드ID는 datatable_reward_card.csv 참고)
        //if (cardIds.isEmpty() == false) cardIds.set(1, "card_health_heal");
        return cardIds;
    }

    private GridCellOverrideDto findCellOverride(ZoneConfigData zoneConfig, int row, int col) {
        List<GridCellOverrideDto> overrides = zoneConfig.getCellOverrides();
        if (overrides == null) return null;
        for (GridCellOverrideDto o : overrides) {
            if (o.getRow() != null && o.getRow() == row && o.getCol() != null && o.getCol() == col)
                return o;
        }
        return null;
    }

    // 클라 UIPanelExplorationGrid.BuildCellEnemyFleets의 "cellData.isBlocked || isStart || isEvent면 생성 대상 제외"와 동일 규칙 —
    // 적함대 절차 생성을 서버에서 제거한 뒤에도 "이 셀에 전투가 있는지"는 셀 타입만으로 그대로 판정 가능(Escape는 전투 있음에 포함)
    private boolean hasCombatCell(ZoneConfigData zoneConfig, int row, int col) {
        GridCellOverrideDto override = findCellOverride(zoneConfig, row, col);
        if (override == null) return true;
        EGridCellType type = override.getType();
        return type != EGridCellType.Blocked && type != EGridCellType.Start && type != EGridCellType.Event;
    }

    // enter-cell 시점에 위치를 즉시 확정해도 되는 셀 — Blocked(도달 불가)/Start(시작점)만 해당.
    // Event(Treasure)는 hasCombatCell()과 달리 여기서 제외하지 않음 — clear-cell 왕복을 거쳐야 트레저 보상을 지급할 수 있기 때문(hasCombatCell은 표준 존 보상/보상카드 지급 여부만 판정하는 별개 기준)
    private boolean canConfirmPositionOnEnter(EGridCellType type) {
        return type == EGridCellType.Blocked || type == EGridCellType.Start;
    }

    private GridCellOverrideDto findCellByType(ZoneConfigData zoneConfig, EGridCellType type) {
        List<GridCellOverrideDto> overrides = zoneConfig.getCellOverrides();
        if (overrides == null) return null;
        for (GridCellOverrideDto o : overrides) {
            if (o.getType() == type) return o;
        }
        return null;
    }

    // 업적(ZoneFullClear) — 존 전체 셀 수에서 클리어 로그가 절대 남지 않는 셀(Blocked/Start)을 뺀, 실제로 클리어 가능한 셀 총수
    // Start는 canConfirmPositionOnEnter()로 clear-cell 왕복 없이 위치만 즉시 확정되므로 zone_cell_clear_log에 절대 기록되지 않음
    private int countNonBlockedCells(ZoneConfigData zoneConfig) {
        int totalCellCount = zoneConfig.getGridWidth() * zoneConfig.getGridHeight();
        int excludedCellCount = 0;
        List<GridCellOverrideDto> overrides = zoneConfig.getCellOverrides();
        if (overrides != null) {
            for (GridCellOverrideDto o : overrides) {
                if (o.getType() == EGridCellType.Blocked || o.getType() == EGridCellType.Start) excludedCellCount++;
            }
        }
        return totalCellCount - excludedCellCount;
    }

    // enter-cell이 발급한 1회용 토큰을 검증 — 통과 시 즉시 무효화(재사용 방지). enter-cell 없이 clear-cell만 반복 호출하는 것을 막는 것이 목적.
    private void validateAndConsumeChallengeToken(ZoneRun run, String requestToken, int cellRow, int cellCol) {
        String expectedCell = cellRow + "-" + cellCol;
        boolean tokenMatches = run.getActiveChallengeToken() != null
                && run.getActiveChallengeToken().equals(requestToken)
                && run.getActiveChallengeCell() != null
                && run.getActiveChallengeCell().equals(expectedCell);
        if (tokenMatches == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CHALLENGE_TOKEN_INVALID);

        run.setActiveChallengeToken(null);
        run.setActiveChallengeCell(null);
        run.setActiveChallengeIssuedAt(null);
    }

    // validateAndConsumeChallengeToken과 동일한 검증(토큰/셀 일치)이지만, 대상이 아직 없는 ZoneRun이 아니라
    // enter-cell이 Redis에 임시 저장해둔 첫 셀 챌린지 — 통과 시 즉시 삭제(재사용 방지)
    private void validateAndConsumePendingZoneRunChallenge(Long commanderId, String requestToken, int zoneNumber, int cellRow, int cellCol) {
        RedisService.PendingZoneRunChallengeInfo pending = redisService.getPendingZoneRunChallenge(commanderId);
        String expectedCell = cellRow + "-" + cellCol;
        boolean matches = pending != null
                && pending.zoneNumber() == zoneNumber
                && pending.cell().equals(expectedCell)
                && pending.token().equals(requestToken);
        if (matches == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CHALLENGE_TOKEN_INVALID);

        redisService.deletePendingZoneRunChallenge(commanderId);
    }

    // 요청 셀이 (fromRow,fromCol) 기준 4방향 인접인지 + Blocked가 아닌지 검증 — 클라가 보낸 좌표를 신뢰하지 않음
    private void validateCellChallenge(ZoneConfigData zoneConfig, int fromRow, int fromCol, int toRow, int toCol) {
        int deltaRow = Math.abs(fromRow - toRow);
        int deltaCol = Math.abs(fromCol - toCol);
        boolean isAdjacent = (deltaRow == 1 && deltaCol == 0) || (deltaRow == 0 && deltaCol == 1);
        if (isAdjacent == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CELL_NOT_ADJACENT);

        GridCellOverrideDto target = findCellOverride(zoneConfig, toRow, toCol);
        if (target != null && target.getType() == EGridCellType.Blocked)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CELL_BLOCKED);
    }

    @Transactional
    public EnterExplorationCellResponse enterExplorationCell(Long commanderId, EnterExplorationCellRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByIndex(request.getZoneNumber());
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_FAIL_ZONE_NOT_FOUND);

        Optional<ZoneRun> activeRunOpt = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS);

        // 다른 존에 진행 중인 런이 있으면 확인 없이는 진행 불가 — ZoneRun은 이제 항상 클리어 로그 최소 1개와 함께 생성되므로
        // (첫 셀 클리어 전엔 ZoneRun 자체가 없음) "진행도 0인 런"이라는 상태가 더 이상 존재하지 않아 조용히 정리할 필요도 없어짐
        if (activeRunOpt.isPresent() && activeRunOpt.get().getZoneNumber() != request.getZoneNumber())
            throw new BusinessException(ServerErrorCode.EXPLORATION_ANOTHER_ZONE_IN_PROGRESS);

        if (activeRunOpt.isPresent()) {
            ZoneRun run = activeRunOpt.get();

            validateCellChallenge(zoneConfig, run.getCurrentRow(), run.getCurrentCol(), request.getCellRow(), request.getCellCol());

            // 재방문(이 런에서 이미 클리어 로그가 있는 셀)은 전투가 없는 게 서버 기준으로 확정된 상태 —
            // 클라의 로컬 클리어 캐시(m_gridData.isCleared)를 신뢰하지 않고 서버가 클리어 로그로 직접 재확인.
            // 토큰 발급 없이 위치만 확정하고 응답(클라는 "빈 셀"과 동일하게 전투 없이 통과 처리)
            String revisitCell = request.getCellRow() + "-" + request.getCellCol();
            boolean isRevisit = zoneCellClearLogRepository.findTopByZoneRunIdAndCellOrderByClearedAtDesc(run.getId(), revisitCell).isPresent();
            if (isRevisit == true) {
                run.setCurrentPosition(request.getCellRow(), request.getCellCol());
                zoneRunRepository.save(run);

                return EnterExplorationCellResponse.builder()
                        .zoneNumber(request.getZoneNumber())
                        .cellRow(request.getCellRow())
                        .cellCol(request.getCellCol())
                        .challengeToken(null)
                        .build();
            }

            // Blocked/Start는 여기서 바로 위치 확정 — validateCellChallenge를 이미 통과했으므로(인접 + Blocked 아님) 이동 가능한 셀인 것은 보장됨.
            // 챌린지 토큰은 발급하지 않고 null로 응답 — 클라가 재방문(challengeToken==null)과 동일하게 처리해 뒤따르는
            // clear-cell 호출을 하지 않게 됨. 여기서 토큰을 발급해버리면 클라가 clear-cell을 또 호출하는데, 그 시점엔
            // run.currentRow/Col이 이미 이 셀로 확정된 뒤라 인접성 검사가 "자기 자신"과 비교돼 항상 실패함(EXPLORATION_CELL_NOT_ADJACENT).
            // Event(Treasure)는 hasCombatCell()상 "적 없음"이지만 clear-cell 왕복으로 보상을 지급해야 하므로 여기서 확정하지 않음(canConfirmPositionOnEnter 참고)
            GridCellOverrideDto enteringCellOverride = findCellOverride(zoneConfig, request.getCellRow(), request.getCellCol());
            EGridCellType enteringCellType = enteringCellOverride != null ? enteringCellOverride.getType() : null;
            if (canConfirmPositionOnEnter(enteringCellType) == true) {
                run.setCurrentPosition(request.getCellRow(), request.getCellCol());
                zoneRunRepository.save(run);

                return EnterExplorationCellResponse.builder()
                        .zoneNumber(request.getZoneNumber())
                        .cellRow(request.getCellRow())
                        .cellCol(request.getCellCol())
                        .challengeToken(null)
                        .build();
            }

            // 이 셀에 대한 1회용 클리어 챌린지 토큰 발급 — clear-cell이 이 토큰 없이는 통과 못 하도록 함(enter 생략한 clear 반복 호출 차단)
            String challengeToken = java.util.UUID.randomUUID().toString();
            run.setActiveChallengeToken(challengeToken);
            run.setActiveChallengeCell(request.getCellRow() + "-" + request.getCellCol());
            run.setActiveChallengeIssuedAt(Instant.now());
            zoneRunRepository.save(run);

            return EnterExplorationCellResponse.builder()
                    .zoneNumber(request.getZoneNumber())
                    .cellRow(request.getCellRow())
                    .cellCol(request.getCellCol())
                    .challengeToken(challengeToken)
                    .build();
        }

        // 진행 중인 런이 아직 없음(첫 셀) — requirePreviousStageCleared 검사 후 zone의 Start 셀을 원점으로 인접성만 검증.
        // ZoneRun은 여기서 만들지 않음 — 클리어에 실제로 성공하는 시점(clearExplorationCell)에야 비로소 생성됨
        if (requirePreviousStageCleared == true && request.getZoneNumber() > commander.getHighestClearedZoneNumber() + 1)
            throw new BusinessException(ServerErrorCode.EXPLORATION_ZONE_LOCKED);

        GridCellOverrideDto startCell = findCellByType(zoneConfig, EGridCellType.Start);
        if (startCell == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_START_CELL_NOT_CONFIGURED);

        validateCellChallenge(zoneConfig, startCell.getRow(), startCell.getCol(), request.getCellRow(), request.getCellCol());

        GridCellOverrideDto enteringCellOverride = findCellOverride(zoneConfig, request.getCellRow(), request.getCellCol());
        EGridCellType enteringCellType = enteringCellOverride != null ? enteringCellOverride.getType() : null;
        if (canConfirmPositionOnEnter(enteringCellType) == true) {
            // Blocked는 위 validateCellChallenge에서 이미 항상 걸러지고 Start도 첫 이동에서는 도달 불가능해 사실상 도달하지
            // 않는 방어적 분기지만, 이 경우엔 뒤따르는 clear-cell 호출 자체가 없으므로(재방문과 동일하게 처리됨) 여기서
            // 바로 ZoneRun을 만들어 위치를 확정해야 함
            ZoneRun run = new ZoneRun(commanderId, request.getZoneNumber(), startCell.getRow(), startCell.getCol(), commander.getResourceMax());
            run.setCurrentPosition(request.getCellRow(), request.getCellCol());
            recordRunStartArchetypes(run, commanderId);
            zoneRunRepository.save(run);

            return EnterExplorationCellResponse.builder()
                    .zoneNumber(request.getZoneNumber())
                    .cellRow(request.getCellRow())
                    .cellCol(request.getCellCol())
                    .challengeToken(null)
                    .build();
        }

        // 실질적으로 항상 도달하는 경로(일반 셀/Event/Escape) — ZoneRun 없이 Redis에 챌린지만 임시 저장
        String challengeToken = java.util.UUID.randomUUID().toString();
        String cell = request.getCellRow() + "-" + request.getCellCol();
        redisService.savePendingZoneRunChallenge(commanderId, request.getZoneNumber(), cell, challengeToken);

        return EnterExplorationCellResponse.builder()
                .zoneNumber(request.getZoneNumber())
                .cellRow(request.getCellRow())
                .cellCol(request.getCellCol())
                .challengeToken(challengeToken)
                .build();
    }

    // 팝업이 필요 없는 재방문 이동(클리어됨/시작 칸) 전용 — 경유 칸마다 왕복하지 않고 목적지 하나만 받아 서버가 자체
    // 데이터(클리어 로그)로 경로 존재 여부를 BFS 검증한 뒤 위치를 그 칸으로 한 번에 갱신. 전투/이벤트가 걸린 칸은 이 API로
    // 이동 불가 — 그런 칸은 기존 enter-cell이 "항상 인접한 마지막 한 칸"으로만 처리함(클라가 이 API로 직전 칸까지 옮긴 뒤 호출)
    @Transactional
    public MoveToExplorationCellResponse moveToExplorationCell(Long commanderId, MoveToExplorationCellRequest request) {
        commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByIndex(request.getZoneNumber());
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_FAIL_ZONE_NOT_FOUND);

        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        GridCellOverrideDto targetOverride = findCellOverride(zoneConfig, request.getCellRow(), request.getCellCol());
        EGridCellType targetType = targetOverride != null ? targetOverride.getType() : null;
        if (targetType == EGridCellType.Blocked || targetType == EGridCellType.Escape)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CELL_PATH_NOT_FOUND);

        Set<String> clearedCells = zoneCellClearLogRepository.findDistinctClearedCellsByZoneRunId(run.getId());
        String targetCell = request.getCellRow() + "-" + request.getCellCol();
        boolean targetIsPassable = targetType == EGridCellType.Start || clearedCells.contains(targetCell);
        if (targetIsPassable == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CELL_PATH_NOT_FOUND);

        boolean reachable = isReachableByBfs(zoneConfig, clearedCells, run.getCurrentRow(), run.getCurrentCol(), request.getCellRow(), request.getCellCol());
        if (reachable == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CELL_PATH_NOT_FOUND);

        run.setCurrentPosition(request.getCellRow(), request.getCellCol());
        zoneRunRepository.save(run);

        return MoveToExplorationCellResponse.builder()
                .cellRow(request.getCellRow())
                .cellCol(request.getCellCol())
                .build();
    }

    // 4방향 BFS — "클리어됨 또는 시작 칸이고 Blocked/Escape가 아닌" 칸만 통과 가능한 노드로 취급
    // (클라 ExplorationGridData.FindPath와 동일 규칙 — 탈출 셀은 클리어 즉시 존이 끝나는 특수 셀이라 경유 노드에서 제외)
    private boolean isReachableByBfs(ZoneConfigData zoneConfig, Set<String> clearedCells, int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow == toRow && fromCol == toCol) return true;

        Queue<int[]> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new int[]{fromRow, fromCol});
        visited.add(fromRow + "-" + fromCol);

        int[] deltaRow = {-1, 1, 0, 0};
        int[] deltaCol = {0, 0, -1, 1};

        while (queue.isEmpty() == false) {
            int[] cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nextRow = cur[0] + deltaRow[i];
                int nextCol = cur[1] + deltaCol[i];
                if (nextRow < 0 || nextRow >= zoneConfig.getGridHeight() || nextCol < 0 || nextCol >= zoneConfig.getGridWidth())
                    continue;

                String key = nextRow + "-" + nextCol;
                if (visited.contains(key)) continue;

                GridCellOverrideDto override = findCellOverride(zoneConfig, nextRow, nextCol);
                EGridCellType type = override != null ? override.getType() : null;
                boolean isBlocked = type == EGridCellType.Blocked;
                boolean isEscape = type == EGridCellType.Escape;
                boolean isPassable = isBlocked == false && isEscape == false
                        && (type == EGridCellType.Start || clearedCells.contains(key));
                if (isPassable == false) continue;

                if (nextRow == toRow && nextCol == toCol) return true;

                visited.add(key);
                queue.add(new int[]{nextRow, nextCol});
            }
        }
        return false;
    }

    @Transactional
    public ClearExplorationCellResponse clearExplorationCell(Long commanderId, ClearExplorationCellRequest request) {
        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByIndex(request.getZoneNumber());
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_FAIL_ZONE_NOT_FOUND);

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        Optional<ZoneRun> activeRunOpt = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber());

        // 첫 셀(이 존에 진행 중인 런이 아직 없음) 여부 — true면 아래에서 Redis 챌린지 검증 후 이 시점에 ZoneRun을 새로 만듦
        boolean isFirstCellOfNewRun = activeRunOpt.isPresent() == false;

        ZoneRun run;
        if (isFirstCellOfNewRun == false) {
            run = activeRunOpt.get();
            validateCellChallenge(zoneConfig, run.getCurrentRow(), run.getCurrentCol(), request.getCellRow(), request.getCellCol());
        } else {
            GridCellOverrideDto startCell = findCellByType(zoneConfig, EGridCellType.Start);
            if (startCell == null)
                throw new BusinessException(ServerErrorCode.EXPLORATION_START_CELL_NOT_CONFIGURED);

            validateCellChallenge(zoneConfig, startCell.getRow(), startCell.getCol(), request.getCellRow(), request.getCellCol());

            validateAndConsumePendingZoneRunChallenge(commanderId, request.getChallengeToken(), request.getZoneNumber(),
                    request.getCellRow(), request.getCellCol());

            run = new ZoneRun(commanderId, request.getZoneNumber(), startCell.getRow(), startCell.getCol(), commander.getResourceMax());
            recordRunStartArchetypes(run, commanderId);
            run = zoneRunRepository.save(run);
        }

        // 재방문(이 런에서 이미 클리어 로그가 있는 셀)은 패스 — 포인트/경험치/보상카드 재지급 없이 위치만 갱신
        // 클라가 재방문 셀 이동 시에도 이 API를 그대로 호출해 서버의 run.currentCell을 동기화함(안 그러면 다음 이동의 인접성 검사가 어긋남)
        // 방금 새로 만든 런(isFirstCellOfNewRun)은 클리어 로그가 있을 수 없어 항상 false
        String cell = request.getCellRow() + "-" + request.getCellCol();
        boolean isRevisit = isFirstCellOfNewRun == false
                && zoneCellClearLogRepository.findTopByZoneRunIdAndCellOrderByClearedAtDesc(run.getId(), cell).isPresent();

        int mineralGained = 0;
        int expGained = 0;
        List<String> rewardCardCandidates = null;
        boolean isTreasureCell = false;
        int treasureMineral = 0;

        if (isRevisit == false) {
            // 최초 클리어(보상 지급)에만 토큰을 요구 — enter-cell 없이 clear-cell 반복 호출로 무한 획득하는 것을 막는 지점.
            // 첫 셀(isFirstCellOfNewRun)은 위에서 이미 Redis 챌린지로 검증·소비했으므로 여기서 다시 검증하지 않음(run에는 애초에 토큰이 없음)
            boolean hasEnemies = hasCombatCell(zoneConfig, request.getCellRow(), request.getCellCol());
            if (isFirstCellOfNewRun == false)
                validateAndConsumeChallengeToken(run, request.getChallengeToken(), request.getCellRow(), request.getCellCol());

            // 존 고정 보상값 지급 — 적 함대 성능(commandCost)과 무관, 함선이 있던 셀만 지급(빈 셀은 0)
            // 광물은 존 기본값에 "이번 런에서 지금까지 보상받은 클리어 횟수 × mineralRewardBonusPerClear"를 더해 클리어를 거듭할수록 늘어남(재방문 등 보상 없는 클리어는 미포함)
            if (hasEnemies == true) {
                int mineralRewardBonusPerClear = gameDataService.getDataTableConfig().getExploration().getMineralRewardBonusPerClear();
                mineralGained = zoneConfig.getMineralReward() + mineralRewardBonusPerClear * run.getRewardedClearCount();
                run.setRewardedClearCount(run.getRewardedClearCount() + 1);
                // 지휘관 경험치 — 보상받는 전투 셀(탈출 셀 포함)을 처음 클리어할 때마다 존의 셀당 경험치 지급(재방문/빈 셀은 0)
                Integer expRewardPerCell = zoneConfig.getCommanderExpRewardPerCell();
                expGained = expRewardPerCell != null ? expRewardPerCell : 0;
            } else {
                mineralGained = 0;
            }

            // 탈출 셀은 보상카드 후보 생성 스킵 — 탈출 셀은 별도의 탈출 확정 흐름을 가짐
            // 후보는 이번 런에서 처음 필요해질 때 1회만 추첨해서 run에 고정 — 이후 셀을 클리어해도 재추첨하지 않고 그대로 재사용(리롤/구매 시에만 갱신)
            GridCellOverrideDto escapeCell = findCellByType(zoneConfig, EGridCellType.Escape);
            boolean isEscapeCell = escapeCell != null && escapeCell.getRow() == request.getCellRow() && escapeCell.getCol() == request.getCellCol();
            if (hasEnemies == true && isEscapeCell == false) {
                rewardCardCandidates = deserializeCardIdList(run.getRewardCardCandidatesJson());
                if (rewardCardCandidates == null) {
                    rewardCardCandidates = rollRewardCardCandidates(null);
                    run.setRewardCardCandidatesJson(serializeCardIdList(rewardCardCandidates));
                }
                run.setRewardCardPopupClosed(false);
            }

            // Event(Treasure) 셀 — 여기서 지급하지 않고 선택 대기만 걸어둠(select-treasure-reward에서 지급). 광물 선택지 액수는 일반 광물과 같은 배율 규칙으로 미리 계산
            GridCellOverrideDto cellOverride = findCellOverride(zoneConfig, request.getCellRow(), request.getCellCol());
            isTreasureCell = cellOverride != null && cellOverride.getType() == EGridCellType.Event
                    && cellOverride.getEventType() == EGridEventType.Treasure;
            if (isTreasureCell == true)
                treasureMineral = zoneConfig.getMineralReward() * TREASURE_MINERAL_MULTIPLIER;

            // Buff_MineralRate 지속버프 — 이번 런에서 지금까지 선택 확정된 카드 기준으로 "앞으로" 받을 광물에 바로 적용(은행/정산 개념 없음)
            float mineralRateMultiplier = computeMineralRateMultiplier(run);
            mineralGained = Math.round(mineralGained * mineralRateMultiplier);
            treasureMineral = Math.round(treasureMineral * mineralRateMultiplier);

            // Buff_MineralFlatBonus 지속버프 — 보상이 있던 셀(일반/Treasure)에만 가산, 보상이 아예 없는 빈 셀에서 공짜로 생기지 않게 방어
            if (mineralGained > 0)
                mineralGained += run.getMineralFlatBonusTotal();
            if (treasureMineral > 0)
                treasureMineral += run.getMineralFlatBonusTotal();

            // VIP 혜택 — 광물/경험치 지급 자체를 2배로
            if (isVipActive(commanderId) == true) {
                mineralGained *= 2;
                expGained *= 2;
                treasureMineral *= 2;
            }

            if (isTreasureCell == true) {
                run.setPendingTreasureCell(cell);
                run.setPendingTreasureMineral(treasureMineral);
            }
        }

        // 경험치는 셀 클리어 즉시 확정 — 이후 존런을 포기/실패해도 잃지 않음
        if (expGained > 0) {
            commander.setExp(commander.getExp() + expGained);
            CommanderLevelUtil.autoLevelUpIfNeeded(commander, gameDataService);
        }

        // 함재기 약탈 광물 — 클라 보고 합계를 그대로 지급(상한 없음), 존 보상용 광물 배율/VIP 2배는 적용하지 않음
        int scavengedMineral = request.getScavengedMineral() != null ? Math.max(0, request.getScavengedMineral()) : 0;
        mineralGained += scavengedMineral;

        // 탐사(argus) 성공 광물 — 약탈과 마찬가지로 클라 보고 합계를 그대로 지급(상한 없음, 존 보상 배율/VIP 2배 미적용). corsair scavenge_mineral 카운터에는 포함하지 않음
        int scoutedMineral = request.getScoutedMineral() != null ? Math.max(0, request.getScoutedMineral()) : 0;
        mineralGained += scoutedMineral;

        validateHealthSnapshot(request.getShipHealthRatios());

        run.setCurrentPosition(request.getCellRow(), request.getCellCol());
        run.setFleetHealthSnapshotJson(serializeHealthSnapshot(request.getShipHealthRatios(), run.getFleetHealthSnapshotJson()));
        // 전투 중엔 서버에 실시간 저장하지 않고 셀 클리어 확정 시점에만 자원을 저장 — 0~resourceMax로 클램프(체력 스냅샷과 동일하게 클라 계산값을 신뢰하되 범위만 방어).
        int previousResource = run.getResource();
        int reportedResource = request.getResource() != null ? request.getResource() : previousResource;
        int finalResource = Math.max(0, Math.min(reportedResource, commander.getResourceMax()));
        int spentResource = Math.max(0, previousResource - finalResource);
        run.setResource(finalResource);

        // 광물 — 클라가 보고한 현재치 위에 서버가 방금 계산한 지급분(존 고정 지급 + 함재기 약탈)을 더함(상한 없이 0 이상만 방어)
        int finalMineralAmount = Math.max(0, request.getMineralAmount()) + mineralGained;
        run.setMineralAmount(finalMineralAmount);
        zoneRunRepository.save(run);

        if (mineralGained > 0) {
            commander.setMineralEarnedTotal(commander.getMineralEarnedTotal() + mineralGained);
            commanderRepository.save(commander);
            dailyCounterService.increment(commanderId, EAchievementConditionType.MineralEarned, mineralGained);
        }

        // 일일퀘스트 전투 통계 — 클라 보고값을 신뢰하되 셀 1회 상한으로 보정
        int reportedKills = request.getEnemyShipKills() != null ? request.getEnemyShipKills() : 0;
        int reportedLaunches = request.getAircraftLaunches() != null ? request.getAircraftLaunches() : 0;
        int cappedKills = Math.min(Math.max(0, reportedKills), k_maxReportedKillsPerCell);
        int cappedLaunches = Math.min(Math.max(0, reportedLaunches), k_maxReportedLaunchesPerCell);
        dailyCounterService.increment(commanderId, EAchievementConditionType.EnemyShipKill, cappedKills);
        dailyCounterService.increment(commanderId, EAchievementConditionType.AircraftLaunch, cappedLaunches);
        dailyCounterService.increment(commanderId, EAchievementConditionType.ScavengedMineral, scavengedMineral);

        // 무기 사용 방식/방어/수리 통계 — 클라 보고값을 그대로 신뢰(0 이상만 방어)
        if (Boolean.TRUE.equals(request.getIsMissileOnlyClear()) == true)
            dailyCounterService.increment(commanderId, EAchievementConditionType.MissileOnlyCellClear, 1);
        if (Boolean.TRUE.equals(request.getIsAircraftOnlyClear()) == true)
            dailyCounterService.increment(commanderId, EAchievementConditionType.AircraftOnlyCellClear, 1);
        if (Boolean.TRUE.equals(request.getIsNoShieldClear()) == true)
            dailyCounterService.increment(commanderId, EAchievementConditionType.NoShieldCellClear, 1);
        int shieldAbsorbAmount = request.getShieldAbsorbAmount() != null ? Math.max(0, request.getShieldAbsorbAmount()) : 0;
        int interceptCount = request.getInterceptCount() != null ? Math.max(0, request.getInterceptCount()) : 0;
        int fleetRepairAmount = request.getFleetRepairAmount() != null ? Math.max(0, request.getFleetRepairAmount()) : 0;
        dailyCounterService.increment(commanderId, EAchievementConditionType.ShieldAbsorb, shieldAbsorbAmount);
        dailyCounterService.increment(commanderId, EAchievementConditionType.InterceptCount, interceptCount);
        dailyCounterService.increment(commanderId, EAchievementConditionType.FleetRepairAmount, fleetRepairAmount);

        // 실드/요격체 해금 단계가 이번 클리어로 완료됐는지 — 완료됐으면 응답에 갱신된 함대를 실어 다음 셀 전투부터 반영
        boolean unlockStageCompleted = false;

        // 아키타입 패시브 카운터 — 클라가 (아키타입, 종류)별로 보고한 값을 그 아키타입에만 누적. 살아있는 동안에만 쌓이는 규칙은 클라가 적립 시점에 보장
        // 이 셀 전투 시점에 함대에 있던 아키타입만 인정하고, 허용된 종류가 아니면 무시.
        // 적 격침/약탈 광물은 아키타입 1개당 이 셀의 총 격침 수/총 약탈 광물을 넘을 수 없음(조작 방어), 나머지는 상한 없음
        if (request.getArchetypeCounters() != null) {
            Set<String> fleetArchetypes = fleetService.getActiveFleetArchetypes(commanderId);
            for (ArchetypeCounterReportDto report : request.getArchetypeCounters()) {
                if (report == null || report.getHullArchetype() == null || report.getCounterType() == null) continue;
                if (k_reportableArchetypeCounters.contains(report.getCounterType()) == false) continue;
                if (fleetArchetypes.contains(report.getHullArchetype()) == false) continue;

                long reportedValue = report.getValue() != null ? report.getValue() : 0L;
                long cappedValue = capFleetScopeCounter(report.getCounterType(), reportedValue, cappedKills, scavengedMineral, spentResource);
                if (hullArchetypeProgressService.onCounterEvent(commanderId, report.getHullArchetype(), report.getCounterType(), cappedValue) == true)
                    unlockStageCompleted = true;
            }
        }

        // Treasure 셀은 선택 전까지 None으로 기록(이벤트 셀 클리어로 집계), 선택 시 실제 종류로 갱신
        ZoneCellClearLog clearLog = new ZoneCellClearLog(run.getId(), request.getCellRow(), request.getCellCol());
        clearLog.setTreasureRewardType(isTreasureCell == true ? ETreasureRewardType.None : null);
        zoneCellClearLogRepository.save(clearLog);

        int rerollRemain = 0;
        if (rewardCardCandidates != null) {
            syncRewardCardRerollCharge(commander);
            rerollRemain = commander.getRewardCardRerollCharge();
        }

        return ClearExplorationCellResponse.builder()
                .mineralGained(mineralGained)
                .scavengedMineral(scavengedMineral)
                .scoutedMineral(scoutedMineral)
                .expGained(expGained)
                .totalExp(commander.getExp())
                .commanderLevel(commander.getCommanderLevel())
                .treasureChoices(isTreasureCell == true ? buildTreasureChoices(treasureMineral) : null)
                .resource(finalResource)
                .mineralAmount(finalMineralAmount)
                .rewardCardCandidates(rewardCardCandidates)
                .rerollRemain(rerollRemain)
                .hullArchetypePassiveProgress(hullArchetypeProgressService.getProgressDtoList(commanderId))
                .fleetInfo(unlockStageCompleted == true ? fleetService.getActiveFleetOrNull(commanderId) : null)
                .build();
    }

    // 이번 런에서 지금까지 구매 확정한 지속버프 카드 중 Buff_MineralRate만 골라 배율 합산 —
    // 클라 RewardCardSessionState.ApplyCard와 동일한 규칙(같은 effectType을 여러 장 고르면 %를 합산: 1 + sum(value1))
    private float computeMineralRateMultiplier(ZoneRun run) {
        List<String> purchasedCardIds = deserializeCardIdList(run.getRewardCardPurchasedIdsJson());
        if (purchasedCardIds == null) return 1f;

        float valueSum = 0f;
        for (String cardId : purchasedCardIds) {
            GameDataService.RewardCardEntry card = gameDataService.getRewardCard(cardId);
            if (card == null || card.isPersistent == false) continue;
            if ("Buff_MineralRate".equals(card.effectType) == false) continue;

            valueSum += card.value1;
        }
        return 1f + valueSum;
    }

    // 셀 클리어와 무관하게 언제든 보상카드 구매 UI를 열기 위한 조회(UIPanelExplorationGrid의 BuyCardButton) — 후보는 run에 고정되어 있으므로
    // 이미 뽑혀있던 후보가 있으면 그걸 그대로 돌려주고(구매 이어서 가능), 없으면(존런 시작 후 처음 여는 경우) 새로 추첨해서 run에 저장
    @Transactional
    public GetRewardCardShopResponse getRewardCardShop(Long commanderId, GetRewardCardShopRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        List<String> candidates = deserializeCardIdList(run.getRewardCardCandidatesJson());
        if (candidates == null) {
            candidates = rollRewardCardCandidates(null);
            run.setRewardCardCandidatesJson(serializeCardIdList(candidates));
        }
        run.setRewardCardPopupClosed(false);
        zoneRunRepository.save(run);

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));
        syncRewardCardRerollCharge(commander);

        return GetRewardCardShopResponse.builder()
                .rewardCardCandidates(candidates)
                .rerollRemain(commander.getRewardCardRerollCharge())
                .build();
    }

    // 보상카드 구매 — 비용(광물) 검증/차감은 클라가 전담, 여기서는 후보 검증 후 구매 이력만 기록하고 후보를 재추첨해서 돌려줌(같은 팝업에서 계속 구매 가능)
    @Transactional
    public PurchaseRewardCardResponse purchaseRewardCard(Long commanderId, PurchaseRewardCardRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        List<String> candidates = deserializeCardIdList(run.getRewardCardCandidatesJson());
        if (candidates == null || candidates.contains(request.getCardId()) == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_INVALID_SELECTION);

        GameDataService.RewardCardEntry card = gameDataService.getRewardCard(request.getCardId());
        if (card == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_INVALID_SELECTION);

        // Buff_MineralRate 등 지속버프는 여기서 즉시 지급하지 않음 — 구매 이력만 run에 누적해두고
        // 다음 셀 클리어(clearExplorationCell)에서 computeMineralRateMultiplier가 이번 런 전체 누적분을 집계해 반영
        List<String> purchasedCardIds = deserializeCardIdList(run.getRewardCardPurchasedIdsJson());
        if (purchasedCardIds == null) purchasedCardIds = new ArrayList<>();
        purchasedCardIds.add(request.getCardId());
        run.setRewardCardPurchasedIdsJson(serializeCardIdList(purchasedCardIds));

        int mineralSpent = Math.max(0, request.getMineralSpent());
        int mineralBefore = run.getMineralAmount();
        int finalMineralAmount = Math.max(0, mineralBefore - mineralSpent);
        run.setMineralAmount(finalMineralAmount);

        // 일일퀘스트 카운터 — 광물 사용량은 보유량을 넘는 보고값을 제외한 실제 차감분
        int actualMineralSpent = mineralBefore - finalMineralAmount;
        dailyCounterService.increment(commanderId, EAchievementConditionType.RewardCardPurchase, 1);
        dailyCounterService.increment(commanderId, EAchievementConditionType.MineralSpent, actualMineralSpent);

        // Buff_MineralFlatBonus는 card.value1이 아니라 "이번에 지불한 비용 그대로"를 앞으로의 클리어마다 가산할 고정값으로 씀 —
        // 재구매할 때마다 더 비싼 단계로 갈아타는 개념이라 누적(+=)이 아니라 대체(=)
        if ("Buff_MineralFlatBonus".equals(card.effectType))
            run.setMineralFlatBonusTotal(mineralSpent);

        // 지금 보여주고 있던 3장(candidates)과 정확히 같은 카드는 재추첨에서 배제 — 같은 효과 종류는 다시 나올 수 있음
        List<String> newCandidates = rollRewardCardCandidates(candidates);
        run.setRewardCardCandidatesJson(serializeCardIdList(newCandidates));
        zoneRunRepository.save(run);

        return PurchaseRewardCardResponse.builder()
                .rewardCardCandidates(newCandidates)
                .mineralAmount(finalMineralAmount)
                .build();
    }

    // 보상카드 팝업을 닫음 — 재접속 시 이 플래그로 "이미 봤고 더 안 살 것"을 구분해 팝업을 다시 띄우지 않음
    @Transactional
    public CloseRewardCardPopupResponse closeRewardCardPopup(Long commanderId, CloseRewardCardPopupRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        run.setRewardCardPopupClosed(true);
        zoneRunRepository.save(run);

        return new CloseRewardCardPopupResponse();
    }

    // 보상카드 다시 뽑기 — 리롤 가능 횟수는 Commander.rewardCardRerollCharge에 영구 저장, 계정 전체 공유(존런 무관), 시간 경과로 자연 충전
    @Transactional
    public RerollRewardCardResponse rerollRewardCard(Long commanderId, RerollRewardCardRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        List<String> currentCandidates = deserializeCardIdList(run.getRewardCardCandidatesJson());
        if (currentCandidates == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_INVALID_SELECTION); // 아직 후보가 한 번도 추첨되지 않은 런은 리롤 대상이 아님

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        syncRewardCardRerollCharge(commander);

        // VIP는 충전 횟수 제한 자체가 없음 — 소모하지 않고 그대로 둠(시간 경과 충전은 계속 진행되므로 VIP가 끝나면 그 시점의 충전량이 그대로 보임)
        boolean isVip = isVipActive(commanderId);
        if (isVip == false) {
            if (commander.getRewardCardRerollCharge() <= 0)
                throw new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_REROLL_LIMIT_EXCEEDED);

            commander.setRewardCardRerollCharge(commander.getRewardCardRerollCharge() - 1);
        }

        // 지금 보여주고 있던 3장(currentCandidates)과 정확히 같은 카드는 재추첨에서 배제 — 같은 효과 종류는 다시 나올 수 있음
        List<String> newCandidates = rollRewardCardCandidates(currentCandidates);
        run.setRewardCardCandidatesJson(serializeCardIdList(newCandidates));
        zoneRunRepository.save(run);

        return RerollRewardCardResponse.builder()
                .rewardCardCandidates(newCandidates)
                .rerollRemain(commander.getRewardCardRerollCharge())
                .build();
    }

    // 리롤 횟수가 0일 때 광고 시청으로 충전 — VIP는 광고 없이 바로 충전(클라에서 광고 재생을 건너뛰고 이 엔드포인트만 호출)
    @Transactional
    public WatchAdForRewardCardRerollChargeResponse watchAdForRewardCardRerollCharge(Long commanderId, WatchAdForRewardCardRerollChargeRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        syncRewardCardRerollCharge(commander);

        DataTableConfig config = gameDataService.getDataTableConfig();
        int maxCharge = config.getExploration().getRewardCardRerollChargeMax();
        int bonus = config.getExploration().getRewardCardRerollChargeAdBonus();
        commander.setRewardCardRerollCharge(Math.min(maxCharge, commander.getRewardCardRerollCharge() + bonus));
        dailyCounterService.increment(commanderId, EAchievementConditionType.AdRewardClaim, 1);

        return WatchAdForRewardCardRerollChargeResponse.builder()
                .rewardCardRerollCharge(commander.getRewardCardRerollCharge())
                .build();
    }

    // commander.rewardCardRerollCharge를 현재 시각 기준으로 충전 — 충전 단위(설정된 분)에 못 미치는 나머지 경과시간은
    // syncedAt을 충전된 만큼만 전진시켜서 보존(다음 호출에서 이어서 누적). commander는 findByIdForUpdate로 조회된 관리 상태 엔티티여야 함
    private void syncRewardCardRerollCharge(Commander commander) {
        DataTableConfig config = gameDataService.getDataTableConfig();
        int maxCharge = config.getExploration().getRewardCardRerollChargeMax();
        int regenMinutes = config.getExploration().getRewardCardRerollChargeRegenMinutes();

        if (commander.getRewardCardRerollCharge() >= maxCharge) {
            commander.setRewardCardRerollChargeSyncedAt(Instant.now());
            return;
        }

        Instant lastSyncedAt = commander.getRewardCardRerollChargeSyncedAt();
        if (lastSyncedAt == null) {
            commander.setRewardCardRerollChargeSyncedAt(Instant.now());
            return;
        }

        long elapsedMinutes = Duration.between(lastSyncedAt, Instant.now()).toMinutes();
        int regenCount = (int) (elapsedMinutes / regenMinutes);
        if (regenCount <= 0) return;

        int newCharge = Math.min(maxCharge, commander.getRewardCardRerollCharge() + regenCount);
        commander.setRewardCardRerollCharge(newCharge);
        commander.setRewardCardRerollChargeSyncedAt(lastSyncedAt.plus(Duration.ofMinutes((long) regenCount * regenMinutes)));
    }

    // 재접속/SpaceScene 재로드로 클라 그리드가 초기화됐을 때, 진행 중인 런의 클리어 셀 목록을 다시 내려줘 방문 표시를 복구시킴
    @Transactional
    public GetActiveZoneRunProgressResponse getActiveZoneRunProgress(Long commanderId) {
        Optional<ZoneRun> activeRunOpt = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS);
        if (activeRunOpt.isPresent() == false) {
            return GetActiveZoneRunProgressResponse.builder()
                    .zoneNumber(0)
                    .clearedCells(new ArrayList<>())
                    .mineralAmount(0)
                    .build();
        }

        ZoneRun run = activeRunOpt.get();
        List<ZoneCellClearLog> clearLogs = zoneCellClearLogRepository.findByZoneRunIdOrderByClearedAtAsc(run.getId());
        List<String> clearedCells = clearLogs.stream().map(ZoneCellClearLog::getCell).toList();

        // 이번 런에서 구매 확정한 카드 전체 — run에 셀 구분 없이 플랫 누적되어 있음
        List<String> purchasedRewardCards = deserializeCardIdList(run.getRewardCardPurchasedIdsJson());
        if (purchasedRewardCards == null) purchasedRewardCards = new ArrayList<>();

        // 보상카드 후보는 있는데 아직 팝업을 닫지 않았으면 — 앱이 꺼지기 전에 안 닫은 경우, 재접속 시 다시 띄워야 함
        List<String> pendingRewardCardCandidates = null;
        int rerollRemain = 0;
        if (run.isRewardCardPopupClosed() == false && run.getRewardCardCandidatesJson() != null) {
            pendingRewardCardCandidates = deserializeCardIdList(run.getRewardCardCandidatesJson());
            Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));
            syncRewardCardRerollCharge(commander);
            rerollRemain = commander.getRewardCardRerollCharge();
        }

        List<TreasureChoiceInfoDto> pendingTreasureChoices = run.getPendingTreasureCell() != null ? buildTreasureChoices(run.getPendingTreasureMineral()) : null;

        return GetActiveZoneRunProgressResponse.builder()
                .zoneNumber(run.getZoneNumber())
                .clearedCells(clearedCells)
                .mineralAmount(run.getMineralAmount())
                .shipHealthRatios(deserializeHealthSnapshot(run.getFleetHealthSnapshotJson()))
                .purchasedRewardCards(purchasedRewardCards)
                .pendingRewardCardCandidates(pendingRewardCardCandidates)
                .rerollRemain(rerollRemain)
                .pendingTreasureChoices(pendingTreasureChoices)
                .build();
    }

    // 탈출 성공/실패 공통 정산 결과 — 지휘관 경험치 확정 지급분 + 런 종료로 회복된 자원 현재치(광물은 은행/정산 없이 이미 즉시 반영된 상태라 여기서 다루지 않음)
    // 업적포인트는 더 이상 여기서 자동 지급하지 않음 — 유저가 업적 패널에서 직접 "받기"를 눌러야 지급(AchievementService.claimAchievement)
    // hasUnclaimedAchievement: 이 정산으로 완료됐을 수 있는 업적을 클라가 바로 알 수 있도록 존런이 끝나는 시점(체크포인트)에 같이 계산
    // unlockStageCompleted: 이 정산으로 실드/요격체 해금 단계가 완료됐는지 — 응답에 갱신된 함대를 실을지 판단
    private record RunSettlement(int resource, boolean hasUnclaimedAchievement, boolean unlockStageCompleted) {}

    // 함대 범위 카운터(적 격침/약탈 광물/절감 자원)의 아키타입 1개당 상한 — 이 셀 서버 집계 총량을 넘지 못함. 그 외 종류는 보고값 그대로
    // 절감 자원은 이번 셀에서 실제로 줄어든 자원량이 상한(절감률 50% 이하에서는 절감량이 실제 지불량을 넘지 않음)
    private static long capFleetScopeCounter(String counterType, long reportedValue, int cappedKills, int scavengedMineral, int spentResource) {
        if (k_counterDestroyEnemyShip.equals(counterType) == true) return Math.min(reportedValue, (long) cappedKills);
        if (k_counterScavengeMineral.equals(counterType) == true) return Math.min(reportedValue, (long) scavengedMineral);
        if (k_counterFleetResourceSaved.equals(counterType) == true) return Math.min(reportedValue, (long) spentResource);
        return reportedValue;
    }

    // 마지막 체력 스냅샷에서 체력 0인(이번 런에서 파괴된) 함선의 아키타입 — 파괴된 함선은 런이 끝날 때까지 되살아나지 않음
    private Set<String> collectDestroyedArchetypes(Long commanderId, ZoneRun run) {
        Set<String> destroyed = new HashSet<>();
        List<ShipHealthRatioInfoDto> snapshot = deserializeHealthSnapshot(run.getFleetHealthSnapshotJson());
        if (snapshot == null || snapshot.isEmpty() == true) return destroyed; // 스냅샷 없음 = 전투 없이 끝난 런, 파괴된 함선 없음

        Map<Integer, String> archetypeBySlot = fleetService.getActiveFleetArchetypeBySlot(commanderId);
        for (ShipHealthRatioInfoDto info : snapshot) {
            if (info == null || info.getPositionIndex() == null) continue;
            boolean isDestroyed = info.getHealthRatio() == null || info.getHealthRatio() <= 0f;
            if (isDestroyed == false) continue;

            String archetype = archetypeBySlot.get(info.getPositionIndex());
            if (archetype != null) destroyed.add(archetype);
        }
        return destroyed;
    }

    // 반환: 해금 효과(unlock_*)가 있는 단계가 이번에 완료됐으면 true
    // 런 시작부터 계속 함대에 있었고, 런이 끝날 때 살아있는 아키타입만 +1
    private boolean addZoneRunClearCounter(Long commanderId, ZoneRun run) {
        String continuous = run.getContinuousArchetypes();
        if (continuous == null || continuous.isEmpty() == true) return false;

        Set<String> destroyedArchetypes = collectDestroyedArchetypes(commanderId, run);
        boolean unlockStageCompleted = false;
        for (String archetype : continuous.split(",")) {
            if (destroyedArchetypes.contains(archetype) == true) continue;
            if (hullArchetypeProgressService.onCounterEvent(commanderId, archetype, k_counterZoneRunClear, 1) == true)
                unlockStageCompleted = true;
        }
        return unlockStageCompleted;
    }

    // 탈출 성공/실패, 포기 공통 정산 — escapeExplorationZone(성공/실패)과 abandonZoneRun(실패 고정)이 공유.
    // isSuccess는 run 상태(ESCAPED/ABANDONED)·최고클리어존 갱신에만 쓰임. 지휘관 경험치는 셀 클리어마다 이미 지급되므로 정산에서는 지급하지 않음
    private RunSettlement settleZoneRun(Commander commander, ZoneRun run, boolean isSuccess) {
        if (isSuccess && run.getZoneNumber() > commander.getHighestClearedZoneNumber())
            commander.setHighestClearedZoneNumber(run.getZoneNumber());

        run.setStatus(isSuccess ? EZoneRunStatus.ESCAPED : EZoneRunStatus.ABANDONED);
        run.setRewardClaimed(true);
        run.setEndedAt(Instant.now());

        commanderRepository.save(commander);
        zoneRunRepository.save(run);

        // 아키타입 패시브 zone_run_clear — 탈출 성공 정산에서만, 런 시작부터 끝까지 계속 함대에 있었고 파괴되지 않은 아키타입에 +1
        boolean unlockStageCompleted = false;
        if (isSuccess == true)
            unlockStageCompleted = addZoneRunClearCounter(commander.getId(), run);

        boolean hasUnclaimedAchievement = achievementService.hasUnclaimedCompletedAchievement(commander.getId());

        // 런이 끝나면 자원은 다음 런 시작 전이라도 즉시 완전 회복된 것으로 취급(다음 ZoneRun 생성 시 이 값으로 초기화되는 것과 동일한 결과)
        return new RunSettlement(commander.getResourceMax(), hasUnclaimedAchievement, unlockStageCompleted);
    }

    @Transactional
    public EscapeExplorationZoneResponse escapeExplorationZone(Long commanderId, EscapeExplorationZoneRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        boolean isSuccess = request.getIsSuccess() != null && request.getIsSuccess();

        if (isSuccess == true) {
            ZoneConfigData zoneConfig = gameDataService.getZoneConfigByIndex(request.getZoneNumber());
            GridCellOverrideDto escapeCell = zoneConfig != null ? findCellByType(zoneConfig, EGridCellType.Escape) : null;
            boolean reachedEscape = escapeCell != null
                    && escapeCell.getRow() == run.getCurrentRow()
                    && escapeCell.getCol() == run.getCurrentCol();
            if (reachedEscape == false)
                throw new BusinessException(ServerErrorCode.EXPLORATION_ESCAPE_NOT_REACHED);

            // 업적(ZoneFullClear) — 이 런에서 Blocked 아닌 셀을 전부 클리어했는지, DB에 남은 클리어 로그만으로 판정(클라 보고값 신뢰 안 함)
            int nonBlockedCellCount = countNonBlockedCells(zoneConfig);
            long distinctClearedCellCount = zoneCellClearLogRepository.countDistinctCellByZoneRunId(run.getId());
            boolean isAllCellsCleared = distinctClearedCellCount >= nonBlockedCellCount;
            boolean alreadyRecorded = commanderZoneFullClearRepository.existsByCommanderIdAndZoneNumber(commanderId, request.getZoneNumber());
            if (isAllCellsCleared == true && alreadyRecorded == false) {
                commanderZoneFullClearRepository.save(new CommanderZoneFullClear(commanderId, request.getZoneNumber()));
            }
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        RunSettlement settlement = settleZoneRun(commander, run, isSuccess);

        return EscapeExplorationZoneResponse.builder()
                .expGained(0) // 경험치는 셀 클리어 시점에 이미 지급됨
                .totalExp(commander.getExp())
                .commanderLevel(commander.getCommanderLevel())
                .highestClearedZoneNumber(commander.getHighestClearedZoneNumber())
                .resource(settlement.resource())
                .hasUnclaimedAchievement(settlement.hasUnclaimedAchievement())
                .hullArchetypePassiveProgress(hullArchetypeProgressService.getProgressDtoList(commanderId))
                .fleetInfo(settlement.unlockStageCompleted() == true ? fleetService.getActiveFleetOrNull(commanderId) : null)
                .build();
    }

    // 포기 정산 — 셀 클리어로 이미 받은 경험치는 그대로 유지되고, 정산 자체로 추가 지급하는 보상은 없음
    @Transactional
    public AbandonZoneRunResponse abandonZoneRun(Long commanderId, AbandonZoneRunRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        RunSettlement settlement = settleZoneRun(commander, run, false);

        return AbandonZoneRunResponse.builder()
                .expGained(0) // 경험치는 셀 클리어 시점에 이미 지급됨
                .totalExp(commander.getExp())
                .commanderLevel(commander.getCommanderLevel())
                .resource(settlement.resource())
                .hasUnclaimedAchievement(settlement.hasUnclaimedAchievement())
                .build();
    }

    // 진행 중인 런의 광물을 소모해 자원 게이지를 목표치까지 채움 — 환산량은 서버가 권위적으로 계산(클라 로컬 미리보기와 동일 공식)
    // 목표치는 [현재치, resourceMax]로 제한. 채울 게 없으면(목표치 이하이거나 광물이 없으면) 아무것도 바꾸지 않고 현재 값을 그대로 반환(에러 아님)
    @Transactional
    public ChargeResourceResponse chargeResource(Long commanderId, ChargeResourceRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        int requestedTarget = request.getTargetResource() != null ? request.getTargetResource() : 0;
        int targetResource = Math.max(run.getResource(), Math.min(requestedTarget, commander.getResourceMax()));
        int neededPoints = targetResource - run.getResource();
        int ratio = gameDataService.getMineralToResourceRatio();
        if (neededPoints > 0 && run.getMineralAmount() > 0 && ratio > 0) {
            int affordableMineral = (neededPoints + ratio - 1) / ratio; // 필요 포인트를 다 채우는 데 드는 광물 수(올림)
            int mineralToSpend = Math.min(affordableMineral, run.getMineralAmount());
            int gainedPoints = Math.min(neededPoints, mineralToSpend * ratio);

            run.setMineralAmount(run.getMineralAmount() - mineralToSpend);
            run.setResource(run.getResource() + gainedPoints);
            zoneRunRepository.save(run);
            dailyCounterService.increment(commanderId, EAchievementConditionType.MineralSpent, mineralToSpend);
        }

        return ChargeResourceResponse.builder()
                .resource(run.getResource())
                .mineralAmount(run.getMineralAmount())
                .build();
    }


    // Treasure 셀 무료 보상 선택지 3개 — 광물/체력회복/전투자원충전 순서 고정
    private List<TreasureChoiceInfoDto> buildTreasureChoices(int treasureMineral) {
        List<TreasureChoiceInfoDto> choices = new ArrayList<>();
        choices.add(TreasureChoiceInfoDto.builder().rewardType(ETreasureRewardType.Mineral).mineralAmount(treasureMineral).ratio(0f).build());
        choices.add(TreasureChoiceInfoDto.builder().rewardType(ETreasureRewardType.ShipHealthHeal).mineralAmount(0).ratio(TREASURE_SHIP_HEALTH_HEAL_RATIO).build());
        choices.add(TreasureChoiceInfoDto.builder().rewardType(ETreasureRewardType.ResourceCharge).mineralAmount(0).ratio(TREASURE_RESOURCE_CHARGE_RATIO).build());
        return choices;
    }

    // Treasure 셀 무료 보상 지급 — 광물/전투자원은 런에 바로 반영, 체력은 저장된 함대 스냅샷에 반영하고 실제 함선 회복은 클라가 같은 비율로 적용
    @Transactional
    public SelectTreasureRewardResponse selectTreasureReward(Long commanderId, SelectTreasureRewardRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        String pendingCell = run.getPendingTreasureCell();
        ETreasureRewardType rewardType = request.getRewardType();
        boolean isValidType = rewardType == ETreasureRewardType.Mineral || rewardType == ETreasureRewardType.ShipHealthHeal
                || rewardType == ETreasureRewardType.ResourceCharge;
        if (pendingCell == null || isValidType == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_TREASURE_INVALID_SELECTION);

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        int mineralGained = 0;
        float ratio = 0f;
        if (rewardType == ETreasureRewardType.Mineral) {
            mineralGained = run.getPendingTreasureMineral();
            run.setMineralAmount(run.getMineralAmount() + mineralGained);
        } else if (rewardType == ETreasureRewardType.ShipHealthHeal) {
            ratio = TREASURE_SHIP_HEALTH_HEAL_RATIO;
            run.setFleetHealthSnapshotJson(healHealthSnapshot(run.getFleetHealthSnapshotJson(), ratio));
        } else {
            ratio = TREASURE_RESOURCE_CHARGE_RATIO;
            int chargeAmount = Math.round(commander.getResourceMax() * ratio);
            int chargedResource = Math.min(commander.getResourceMax(), run.getResource() + chargeAmount);
            run.setResource(chargedResource);
        }

        run.setPendingTreasureCell(null);
        run.setPendingTreasureMineral(0);
        zoneRunRepository.save(run);

        zoneCellClearLogRepository.findTopByZoneRunIdAndCellOrderByClearedAtDesc(run.getId(), pendingCell)
                .ifPresent(clearLog -> {
                    clearLog.setTreasureRewardType(rewardType);
                    zoneCellClearLogRepository.save(clearLog);
                });

        if (mineralGained > 0) {
            commander.setMineralEarnedTotal(commander.getMineralEarnedTotal() + mineralGained);
            commanderRepository.save(commander);
            dailyCounterService.increment(commanderId, EAchievementConditionType.MineralEarned, mineralGained);
        }

        return SelectTreasureRewardResponse.builder()
                .rewardType(rewardType)
                .ratio(ratio)
                .mineralGained(mineralGained)
                .mineralAmount(run.getMineralAmount())
                .resource(run.getResource())
                .build();
    }

    // 저장된 함대 체력 스냅샷에 회복 비율을 더함 — 파괴된 함선(0)은 클라 HealAllShipsByRatio와 같이 회복하지 않음, 스냅샷이 없으면(만피) 그대로
    private String healHealthSnapshot(String snapshotJson, float healRatio) {
        List<ShipHealthRatioInfoDto> snapshot = deserializeHealthSnapshot(snapshotJson);
        if (snapshot == null) return snapshotJson;

        for (ShipHealthRatioInfoDto info : snapshot) {
            if (info.getHealthRatio() == null || info.getHealthRatio() <= 0f) continue;
            float healedRatio = Math.min(HEALTH_RATIO_MAX, info.getHealthRatio() + healRatio);
            info.setHealthRatio(healedRatio);
        }
        return serializeHealthSnapshot(snapshot, snapshotJson);
    }

}
