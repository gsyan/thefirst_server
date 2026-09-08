// 탐사 그리드 존 진행(ZoneRun) 서비스 — 셀 입장/클리어/탈출/포기, 탐험 포인트 정산, 지휘력 최대치 구매
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.ZoneCellClearLog;
import com.bk.sbs.entity.ZoneRun;
import com.bk.sbs.enums.EGridCellType;
import com.bk.sbs.enums.EGridEventType;
import com.bk.sbs.enums.ETreasureRewardType;
import com.bk.sbs.enums.EZoneRunStatus;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ExplorationService {

    private final CommanderRepository commanderRepository;
    private final ZoneRunRepository zoneRunRepository;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final GameDataService gameDataService;
    private final ObjectMapper objectMapper;
    private final AchievementService achievementService;

    // false면 highestClearedZoneNumber 검사를 건너뜀 — 웨이브 밸런스 테스트용(application.properties)
    @Value("${zone.require-previous-stage-cleared:true}")
    private boolean requirePreviousStageCleared;

    public ExplorationService(CommanderRepository commanderRepository, ZoneRunRepository zoneRunRepository,
                               ZoneCellClearLogRepository zoneCellClearLogRepository,
                               GameDataService gameDataService, ObjectMapper objectMapper,
                               AchievementService achievementService) {
        this.commanderRepository = commanderRepository;
        this.zoneRunRepository = zoneRunRepository;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.gameDataService = gameDataService;
        this.objectMapper = objectMapper;
        this.achievementService = achievementService;
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
    // 모듈 repair(시간당 회복)를 정밀 계산하지 않고 넉넉한 고정 여유값으로 흡수 — 전투 지속시간이 길수록 허용치도 커짐.
    // 오탐(정상 유저 차단)이 치트 차단 실패보다 훨씬 나쁘므로 넉넉하게 잡음. 정밀 계산은 알려진 한계로 남겨둠.
    private static final float HEALTH_RATIO_TIME_MARGIN_PER_SEC = 0.01f;

    // Treasure(Event) 셀 보상 — 3종 중 1개를 매 클리어마다 진짜 랜덤(java.util.Random 새 인스턴스)으로 지급
    private static final ETreasureRewardType[] TREASURE_REWARD_POOL = {
            ETreasureRewardType.ExplorationPoint, ETreasureRewardType.ShipHealthHeal, ETreasureRewardType.TacticPowerRestore
    };
    private static final int TREASURE_EXPLORATION_POINT_MULTIPLIER = 3; // 그 존의 일반 전투 셀 보상의 3배
    private static final float TREASURE_SHIP_HEALTH_HEAL_RATIO = 0.5f;
    private static final float TREASURE_TACTIC_POWER_RESTORE_RATIO = 1.0f;

    // 체력 비율 범위(0~1), 함선 구성 일치, 직전 스냅샷 대비 증가폭(허용치: 회복 카드 효과 + 시간 여유값) 검증
    private void validateHealthSnapshot(Long commanderId, ZoneRun run, List<ShipHealthRatioInfoDto> reported) {
        if (reported == null || reported.isEmpty()) return;

        for (ShipHealthRatioInfoDto info : reported) {
            if (info.getHealthRatio() == null) continue;
            if (info.getHealthRatio() < HEALTH_RATIO_MIN || info.getHealthRatio() > HEALTH_RATIO_MAX)
                throw new BusinessException(ServerErrorCode.EXPLORATION_FLEET_HEALTH_INVALID);
        }

        List<ShipHealthRatioInfoDto> previous = deserializeHealthSnapshot(run.getFleetHealthSnapshotJson());
        if (previous == null || previous.isEmpty()) return;

        float healBonus = getPendingHealthHealBonus(run);
        long elapsedSeconds = 0;
        if (run.getActiveChallengeIssuedAt() != null)
            elapsedSeconds = Math.max(0, Instant.now().getEpochSecond() - run.getActiveChallengeIssuedAt().getEpochSecond());
        float allowedIncrease = healBonus + (elapsedSeconds * HEALTH_RATIO_TIME_MARGIN_PER_SEC);

        for (ShipHealthRatioInfoDto info : reported) {
            if (info.getHealthRatio() == null || info.getPositionIndex() == null) continue;
            Optional<ShipHealthRatioInfoDto> prevOpt = previous.stream()
                    .filter(p -> p.getPositionIndex() != null && p.getPositionIndex().equals(info.getPositionIndex()))
                    .findFirst();
            if (prevOpt.isEmpty()) continue;

            float increase = info.getHealthRatio() - prevOpt.get().getHealthRatio();
            if (increase > allowedIncrease)
                throw new BusinessException(ServerErrorCode.EXPLORATION_FLEET_HEALTH_INVALID);
        }
    }

    // 직전 클리어 로그에서 선택 확정된 보상카드 또는 Treasure 당첨이 체력 즉시회복 계열이면 그 회복량을 허용 증가치로 반환
    private float getPendingHealthHealBonus(ZoneRun run) {
        List<ZoneCellClearLog> clearLogs = zoneCellClearLogRepository.findByZoneRunIdOrderByClearedAtAsc(run.getId());
        if (clearLogs.isEmpty()) return 0f;

        ZoneCellClearLog lastLog = clearLogs.get(clearLogs.size() - 1);

        if (lastLog.getTreasureRewardType() == ETreasureRewardType.ShipHealthHeal)
            return TREASURE_SHIP_HEALTH_HEAL_RATIO;

        String selectedCardId = lastLog.getRewardCardSelectedId();
        if (selectedCardId == null) return 0f;

        GameDataService.RewardCardEntry card = gameDataService.getRewardCard(selectedCardId);
        if (card == null) return 0f;

        if ("Instant_HealthHeal".equals(card.effectType))
            return card.value1;

        return 0f;
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

    // 가중치 랜덤으로 후보 3개 추첨 — 결과는 ZoneCellClearLog.rewardCardCandidatesJson에 그대로 저장되므로 재계산할 필요 없음(confirmRewardCard 검증도 저장값 기준)
    private List<String> rollRewardCardCandidates() {
        List<GameDataService.RewardCardEntry> pool = gameDataService.getRewardCardList();
        if (pool.isEmpty()) return null;

        List<GameDataService.RewardCardEntry> picked = RewardCardSelector.selectCandidates(pool, 3, new java.util.Random());

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

    // clear-cell 최소 경과시간 — enter-cell 직후 클리어 요청이 오면(전투를 생략한 것이 명백하므로) 거부. 정상 전투는 이보다 훨씬 오래 걸리므로 넉넉하게 잡음
    private static final long CHALLENGE_TOKEN_MIN_ELAPSED_MILLIS = 2000L;

    // enter-cell이 발급한 1회용 토큰을 검증 — 통과 시 즉시 무효화(재사용 방지). enter-cell 없이 clear-cell만 반복 호출하는 것을 막는 것이 목적.
    // requireMinElapsedTime=false면 최소 경과시간 검사를 건너뜀 — Event(Treasure) 등 애초에 전투가 없는 셀은 "전투를 생략했다"는 의심 자체가 성립하지 않음
    private void validateAndConsumeChallengeToken(ZoneRun run, String requestToken, int cellRow, int cellCol, boolean requireMinElapsedTime) {
        String expectedCell = cellRow + "-" + cellCol;
        boolean tokenMatches = run.getActiveChallengeToken() != null
                && run.getActiveChallengeToken().equals(requestToken)
                && run.getActiveChallengeCell() != null
                && run.getActiveChallengeCell().equals(expectedCell);
        if (tokenMatches == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CHALLENGE_TOKEN_INVALID);

        if (requireMinElapsedTime == true) {
            long elapsedMillis = Instant.now().toEpochMilli() - run.getActiveChallengeIssuedAt().toEpochMilli();
            if (elapsedMillis < CHALLENGE_TOKEN_MIN_ELAPSED_MILLIS)
                throw new BusinessException(ServerErrorCode.EXPLORATION_CHALLENGE_TOKEN_INVALID);
        }

        run.setActiveChallengeToken(null);
        run.setActiveChallengeCell(null);
        run.setActiveChallengeIssuedAt(null);
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

        // 다른 존에 진행 중인 런이 있어도, 그 런에서 셀을 하나도 클리어하지 못했다면(대치화면만 보고 물러났거나 첫 전투
        // 클리어 전에 퇴각) 실질적으로 "진행 중"이 아니므로 확인 없이 조용히 종료(0포인트 정산)하고 새 런으로 진행
        if (activeRunOpt.isPresent() && activeRunOpt.get().getZoneNumber() != request.getZoneNumber()) {
            ZoneRun otherZoneRun = activeRunOpt.get();
            boolean hasAnyProgress = zoneCellClearLogRepository.existsByZoneRunId(otherZoneRun.getId());
            log.info("[enterExplorationCell] 다른 존 런 감지: commanderId={}, otherRunId={}, otherZone={}, requestedZone={}, hasAnyProgress={}",
                    commanderId, otherZoneRun.getId(), otherZoneRun.getZoneNumber(), request.getZoneNumber(), hasAnyProgress);
            if (hasAnyProgress == true)
                throw new BusinessException(ServerErrorCode.EXPLORATION_ANOTHER_ZONE_IN_PROGRESS);

            settleZoneRun(commander, otherZoneRun, false);
            log.info("[enterExplorationCell] 진행 없는 런 조용히 종료함: otherRunId={}", otherZoneRun.getId());
            activeRunOpt = Optional.empty();
        }

        ZoneRun run;
        if (activeRunOpt.isPresent()) {
            run = activeRunOpt.get();

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
        } else {
            if (requirePreviousStageCleared == true && request.getZoneNumber() > commander.getHighestClearedZoneNumber() + 1)
                throw new BusinessException(ServerErrorCode.EXPLORATION_ZONE_LOCKED);

            GridCellOverrideDto startCell = findCellByType(zoneConfig, EGridCellType.Start);
            if (startCell == null)
                throw new BusinessException(ServerErrorCode.EXPLORATION_START_CELL_NOT_CONFIGURED);

            validateCellChallenge(zoneConfig, startCell.getRow(), startCell.getCol(), request.getCellRow(), request.getCellCol());

            run = new ZoneRun(commanderId, request.getZoneNumber(), startCell.getRow(), startCell.getCol(), commander.getTacticPowerMax());
            run = zoneRunRepository.save(run);
        }

        // 이 셀에 대한 1회용 클리어 챌린지 토큰 발급 — clear-cell이 이 토큰 없이는 통과 못 하도록 함(enter 생략한 clear 반복 호출 차단)
        String challengeToken = java.util.UUID.randomUUID().toString();
        run.setActiveChallengeToken(challengeToken);
        run.setActiveChallengeCell(request.getCellRow() + "-" + request.getCellCol());
        run.setActiveChallengeIssuedAt(Instant.now());
        zoneRunRepository.save(run);

        // Blocked/Start만 여기서 바로 위치 확정 — validateCellChallenge를 이미 통과했으므로(인접 + Blocked 아님) 이동 가능한 셀인 것은 보장됨.
        // Event(Treasure)는 hasCombatCell()상 "적 없음"이지만 clear-cell 왕복으로 보상을 지급해야 하므로 여기서 확정하지 않음(canConfirmPositionOnEnter 참고)
        GridCellOverrideDto enteringCellOverride = findCellOverride(zoneConfig, request.getCellRow(), request.getCellCol());
        EGridCellType enteringCellType = enteringCellOverride != null ? enteringCellOverride.getType() : null;
        if (canConfirmPositionOnEnter(enteringCellType) == true) {
            run.setCurrentPosition(request.getCellRow(), request.getCellCol());
            zoneRunRepository.save(run);
        }

        return EnterExplorationCellResponse.builder()
                .zoneNumber(request.getZoneNumber())
                .cellRow(request.getCellRow())
                .cellCol(request.getCellCol())
                .challengeToken(challengeToken)
                .build();
    }

    @Transactional
    public ClearExplorationCellResponse clearExplorationCell(Long commanderId, ClearExplorationCellRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByIndex(request.getZoneNumber());
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_FAIL_ZONE_NOT_FOUND);

        validateCellChallenge(zoneConfig, run.getCurrentRow(), run.getCurrentCol(), request.getCellRow(), request.getCellCol());

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        // 재방문(이 런에서 이미 클리어 로그가 있는 셀)은 패스 — 포인트/경험치/보상카드 재지급 없이 위치만 갱신
        // 클라가 재방문 셀 이동 시에도 이 API를 그대로 호출해 서버의 run.currentCell을 동기화함(안 그러면 다음 이동의 인접성 검사가 어긋남)
        String cell = request.getCellRow() + "-" + request.getCellCol();
        boolean isRevisit = zoneCellClearLogRepository.findTopByZoneRunIdAndCellOrderByClearedAtDesc(run.getId(), cell).isPresent();

        int pointsGained = 0;
        int expGained = 0;
        List<String> rewardCardCandidates = null;
        ETreasureRewardType treasureRewardType = ETreasureRewardType.None;
        float treasureRewardRatio = 0f;

        if (isRevisit == false) {
            // 최초 클리어(보상 지급)에만 토큰을 요구 — enter-cell 없이 clear-cell 반복 호출로 무한 획득하는 것을 막는 지점.
            // 최소 경과시간 검사는 전투가 있는 셀에서만(hasEnemies) 적용 — Event(Treasure)는 애초에 전투가 없어 "생략" 의심이 성립하지 않고,
            // 실제로 enter-cell 직후 곧바로 clear-cell을 호출하는 정상 흐름이라 검사를 걸면 항상 실패함
            boolean hasEnemies = hasCombatCell(zoneConfig, request.getCellRow(), request.getCellCol());
            validateAndConsumeChallengeToken(run, request.getChallengeToken(), request.getCellRow(), request.getCellCol(), hasEnemies);

            // 존 고정 보상값 적립 — 적 함대 성능(commandCost)과 무관, 함선이 있던 셀만 지급(빈 셀은 0)
            // Buff_ExplorationPointRate 배율은 여기서 적용하지 않음 — 적립(banked)은 항상 고정값 그대로 쌓고,
            // 배율은 탈출/포기 확정(settleZoneRun) 시점에 최종 적립 총액에 한 번만 곱함
            pointsGained = hasEnemies ? zoneConfig.getExplorationPointReward() : 0;
            expGained    = hasEnemies ? zoneConfig.getCommanderExpReward()     : 0;

            // 탈출 셀은 보상카드 후보 생성 스킵 — 탈출 셀은 별도의 탈출 확정 흐름을 가짐
            GridCellOverrideDto escapeCell = findCellByType(zoneConfig, EGridCellType.Escape);
            boolean isEscapeCell = escapeCell != null && escapeCell.getRow() == request.getCellRow() && escapeCell.getCol() == request.getCellCol();
            rewardCardCandidates = (hasEnemies == true && isEscapeCell == false) ? rollRewardCardCandidates() : null;

            // Event(Treasure) 셀 — 표준 존 보상/보상카드와는 별개로 3종 중 1개를 진짜 랜덤(매 호출 새 Random 인스턴스)으로 지급
            GridCellOverrideDto cellOverride = findCellOverride(zoneConfig, request.getCellRow(), request.getCellCol());
            boolean isTreasureCell = cellOverride != null && cellOverride.getType() == EGridCellType.Event
                    && cellOverride.getEventType() == EGridEventType.Treasure;
            if (isTreasureCell == true) {
                treasureRewardType = TREASURE_REWARD_POOL[new java.util.Random().nextInt(TREASURE_REWARD_POOL.length)];
                if (treasureRewardType == ETreasureRewardType.ExplorationPoint)
                    pointsGained = zoneConfig.getExplorationPointReward() * TREASURE_EXPLORATION_POINT_MULTIPLIER;
                else if (treasureRewardType == ETreasureRewardType.ShipHealthHeal)
                    treasureRewardRatio = TREASURE_SHIP_HEALTH_HEAL_RATIO;
                else if (treasureRewardType == ETreasureRewardType.TacticPowerRestore)
                    treasureRewardRatio = TREASURE_TACTIC_POWER_RESTORE_RATIO;
            }
        }

        validateHealthSnapshot(commanderId, run, request.getShipHealthRatios());

        run.setCurrentPosition(request.getCellRow(), request.getCellCol());
        run.setExplorationPointBanked(run.getExplorationPointBanked() + pointsGained);
        run.setCommanderExpBanked(run.getCommanderExpBanked() + expGained);
        run.setFleetHealthSnapshotJson(serializeHealthSnapshot(request.getShipHealthRatios(), run.getFleetHealthSnapshotJson()));
        // 전투 중엔 서버에 실시간 저장하지 않고 셀 클리어 확정 시점에만 전술력을 저장 — 0~tacticPowerMax로 클램프(체력 스냅샷과 동일하게 클라 계산값을 신뢰하되 범위만 방어).
        // TacticPowerRestore 트레저 당첨 시에는 클라 보고값과 무관하게 전액 회복으로 덮어씀(서버 확정값)
        int reportedTacticPower = request.getTacticPower() != null ? request.getTacticPower() : run.getTacticPower();
        int clampedTacticPower = Math.max(0, Math.min(reportedTacticPower, commander.getTacticPowerMax()));
        int finalTacticPower = treasureRewardType == ETreasureRewardType.TacticPowerRestore ? commander.getTacticPowerMax() : clampedTacticPower;
        run.setTacticPower(finalTacticPower);
        zoneRunRepository.save(run);

        ZoneCellClearLog clearLog = new ZoneCellClearLog(run.getId(), request.getCellRow(), request.getCellCol());
        clearLog.setRewardCardCandidatesJson(serializeCardIdList(rewardCardCandidates));
        clearLog.setTreasureRewardType(treasureRewardType == ETreasureRewardType.None ? null : treasureRewardType);
        zoneCellClearLogRepository.save(clearLog);

        return ClearExplorationCellResponse.builder()
                .explorationPointGained(pointsGained)
                .expGained(expGained)
                .treasureRewardType(treasureRewardType)
                .treasureRewardRatio(treasureRewardRatio)
                .tacticPower(finalTacticPower)
                .rewardCardCandidates(rewardCardCandidates)
                .build();
    }

    // 이번 런에서 지금까지 선택 확정된 지속버프 카드 중 Buff_ExplorationPointRate만 골라 배율 합산 —
    // 클라 RewardCardSessionState.ApplyCard와 동일한 규칙(같은 effectType을 여러 장 고르면 %를 합산: 1 + sum(value1))
    private float computeExplorationPointRateMultiplier(Long zoneRunId) {
        List<ZoneCellClearLog> logs = zoneCellClearLogRepository.findByZoneRunIdOrderByClearedAtAsc(zoneRunId);
        float valueSum = 0f;
        for (ZoneCellClearLog log : logs) {
            String selectedCardId = log.getRewardCardSelectedId();
            if (selectedCardId == null) continue;

            GameDataService.RewardCardEntry card = gameDataService.getRewardCard(selectedCardId);
            if (card == null || card.isPersistent == false) continue;
            if ("Buff_ExplorationPointRate".equals(card.effectType) == false) continue;

            valueSum += card.value1;
        }
        return 1f + valueSum;
    }

    @Transactional
    public ConfirmRewardCardResponse confirmRewardCard(Long commanderId, ConfirmRewardCardRequest request) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .filter(r -> r.getZoneNumber() == request.getZoneNumber())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        String cell = request.getCellRow() + "-" + request.getCellCol();
        ZoneCellClearLog clearLog = zoneCellClearLogRepository.findTopByZoneRunIdAndCellOrderByClearedAtDesc(run.getId(), cell)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_INVALID_SELECTION));

        if (clearLog.getRewardCardSelectedId() != null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_INVALID_SELECTION); // 이미 선택 확정된 클리어 로그 — 중복 확정 방지

        List<String> candidates = deserializeCardIdList(clearLog.getRewardCardCandidatesJson());
        if (candidates == null || candidates.contains(request.getSelectedCardId()) == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_INVALID_SELECTION);

        GameDataService.RewardCardEntry card = gameDataService.getRewardCard(request.getSelectedCardId());
        if (card == null)
            throw new BusinessException(ServerErrorCode.EXPLORATION_REWARD_CARD_INVALID_SELECTION);

        int explorationPointGained = 0;
        if ("Instant_ExplorationPointFlat".equals(card.effectType)) {
            explorationPointGained = (int) card.value1;
            run.setExplorationPointBanked(run.getExplorationPointBanked() + explorationPointGained);
            zoneRunRepository.save(run);
        }

        clearLog.setRewardCardSelectedId(request.getSelectedCardId());
        zoneCellClearLogRepository.save(clearLog);

        return ConfirmRewardCardResponse.builder()
                .selectedCardId(request.getSelectedCardId())
                .explorationPointGained(explorationPointGained)
                .build();
    }

    // 재접속/SpaceScene 재로드로 클라 그리드가 초기화됐을 때, 진행 중인 런의 클리어 셀 목록을 다시 내려줘 방문 표시를 복구시킴
    @Transactional
    public GetActiveZoneRunProgressResponse getActiveZoneRunProgress(Long commanderId) {
        Optional<ZoneRun> activeRunOpt = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS);
        if (activeRunOpt.isPresent() == false) {
            return GetActiveZoneRunProgressResponse.builder()
                    .zoneNumber(0)
                    .clearedCells(new ArrayList<>())
                    .explorationPointBanked(0)
                    .commanderExpBanked(0)
                    .build();
        }

        ZoneRun run = activeRunOpt.get();
        List<ZoneCellClearLog> clearLogs = zoneCellClearLogRepository.findByZoneRunIdOrderByClearedAtAsc(run.getId());
        List<String> clearedCells = clearLogs.stream().map(ZoneCellClearLog::getCell).toList();

        // 이번 런에서 선택 확정한 카드 전체 — 별도 저장 없이 로그에서 재구성(정규화, ZoneRun에 중복 저장하지 않음)
        List<String> selectedRewardCards = clearLogs.stream()
                .map(ZoneCellClearLog::getRewardCardSelectedId)
                .filter(java.util.Objects::nonNull)
                .toList();

        // 마지막 클리어 로그가 카드 후보는 있는데 아직 선택 확정 전이면 — 팝업이 뜨기 전에 앱이 꺼진 경우, 재접속 시 다시 띄워야 함
        List<String> pendingRewardCardCandidates = null;
        if (clearLogs.isEmpty() == false) {
            ZoneCellClearLog lastLog = clearLogs.get(clearLogs.size() - 1);
            if (lastLog.getRewardCardSelectedId() == null)
                pendingRewardCardCandidates = deserializeCardIdList(lastLog.getRewardCardCandidatesJson());
        }

        return GetActiveZoneRunProgressResponse.builder()
                .zoneNumber(run.getZoneNumber())
                .clearedCells(clearedCells)
                .explorationPointBanked(run.getExplorationPointBanked())
                .commanderExpBanked(run.getCommanderExpBanked())
                .shipHealthRatios(deserializeHealthSnapshot(run.getFleetHealthSnapshotJson()))
                .selectedRewardCards(selectedRewardCards)
                .pendingRewardCardCandidates(pendingRewardCardCandidates)
                .build();
    }

    // 탈출 성공/실패 공통 정산 결과 — 탐험 포인트/지휘관 경험치 확정 지급분 + 런 종료로 회복된 전술력 현재치
    // 업적포인트는 더 이상 여기서 자동 지급하지 않음 — 유저가 업적 패널에서 직접 "받기"를 눌러야 지급(AchievementService.claimAchievement)
    // hasUnclaimedAchievement: 이 정산으로 완료됐을 수 있는 업적을 클라가 바로 알 수 있도록 존런이 끝나는 시점(체크포인트)에 같이 계산
    private record RunSettlement(int pointPayout, int expPayout, int tacticPower, boolean hasUnclaimedAchievement) {}

    // 탈출 성공/실패 공통 정산 — escapeExplorationZone(성공/실패)과 abandonZoneRun(실패 고정)이 공유
    private RunSettlement settleZoneRun(Commander commander, ZoneRun run, boolean isSuccess) {
        // Buff_ExplorationPointRate 배율은 여기(최종 확정 지급 시점)에서만 한 번 적용 — 성공/실패(50%) 여부와 무관하게
        // 이번 런에서 선택 확정된 카드 기준으로 최종 적립 총액에 곱함. 반올림 대신 올림 사용 —
        // 배율이 작을 때(예: 1% 카드 1장, 적립 30) Math.round(30*1.01f)=30으로 뭉개져 카드 효과가 사라지는 것을 방지
        float pointRateMultiplier = computeExplorationPointRateMultiplier(run.getId());
        int bankedPointWithRate = (int) Math.ceil(run.getExplorationPointBanked() * pointRateMultiplier);
        int pointPayout = isSuccess ? bankedPointWithRate : bankedPointWithRate / 2;
        int expPayout   = isSuccess ? run.getCommanderExpBanked()     : run.getCommanderExpBanked() / 2;

        commander.setExplorationPoint(commander.getExplorationPoint() + pointPayout);
        commander.setExplorationPointEarnedTotal(commander.getExplorationPointEarnedTotal() + pointPayout);
        commander.setExp(commander.getExp() + expPayout);
        CommanderLevelUtil.autoLevelUpIfNeeded(commander, gameDataService);
        if (isSuccess && run.getZoneNumber() > commander.getHighestClearedZoneNumber())
            commander.setHighestClearedZoneNumber(run.getZoneNumber());

        run.setStatus(isSuccess ? EZoneRunStatus.ESCAPED : EZoneRunStatus.ABANDONED);
        run.setRewardClaimed(true);
        run.setEndedAt(Instant.now());

        commanderRepository.save(commander);
        zoneRunRepository.save(run);

        boolean hasUnclaimedAchievement = achievementService.hasUnclaimedCompletedAchievement(commander.getId());

        // 런이 끝나면 전술력은 다음 런 시작 전이라도 즉시 완전 회복된 것으로 취급(다음 ZoneRun 생성 시 이 값으로 초기화되는 것과 동일한 결과)
        return new RunSettlement(pointPayout, expPayout, commander.getTacticPowerMax(), hasUnclaimedAchievement);
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
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        RunSettlement settlement = settleZoneRun(commander, run, isSuccess);

        return EscapeExplorationZoneResponse.builder()
                .explorationPointGained(settlement.pointPayout())
                .explorationPointRemain(commander.getExplorationPoint())
                .expGained(settlement.expPayout())
                .totalExp(commander.getExp())
                .commanderLevel(commander.getCommanderLevel())
                .highestClearedZoneNumber(commander.getHighestClearedZoneNumber())
                .tacticPower(settlement.tacticPower())
                .hasUnclaimedAchievement(settlement.hasUnclaimedAchievement())
                .build();
    }

    @Transactional
    public AbandonZoneRunResponse abandonZoneRun(Long commanderId) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        RunSettlement settlement = settleZoneRun(commander, run, false);

        return AbandonZoneRunResponse.builder()
                .explorationPointGained(settlement.pointPayout())
                .explorationPointRemain(commander.getExplorationPoint())
                .expGained(settlement.expPayout())
                .totalExp(commander.getExp())
                .commanderLevel(commander.getCommanderLevel())
                .tacticPower(settlement.tacticPower())
                .hasUnclaimedAchievement(settlement.hasUnclaimedAchievement())
                .build();
    }

    // 교환비 1:1 — amount는 클라이언트가 지정, 소모 탐험 포인트와 증가 지휘력 최대치가 동일
    @Transactional
    public IncreaseCommandPowerMaxResponse increaseCommandPowerMax(Long commanderId, int amount) {
        if (amount <= 0)
            throw new BusinessException(ServerErrorCode.EXPLORATION_POINT_INSUFFICIENT);

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        if (commander.getExplorationPoint() < amount)
            throw new BusinessException(ServerErrorCode.EXPLORATION_POINT_INSUFFICIENT);

        commander.setExplorationPoint(commander.getExplorationPoint() - amount);
        commander.setCommandPowerMax(commander.getCommandPowerMax() + amount);
        commanderRepository.save(commander);

        return IncreaseCommandPowerMaxResponse.builder()
                .commandPowerMax(commander.getCommandPowerMax())
                .explorationPointRemain(commander.getExplorationPoint())
                .build();
    }

    // 교환비 1:1 — increaseCommandPowerMax와 동일 패턴, 탐험 포인트를 소모해 전술력 최대치를 영구 증가
    @Transactional
    public IncreaseTacticPowerMaxResponse increaseTacticPowerMax(Long commanderId, int amount) {
        if (amount <= 0)
            throw new BusinessException(ServerErrorCode.EXPLORATION_POINT_INSUFFICIENT);

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        if (commander.getExplorationPoint() < amount)
            throw new BusinessException(ServerErrorCode.EXPLORATION_POINT_INSUFFICIENT);

        commander.setExplorationPoint(commander.getExplorationPoint() - amount);
        commander.setTacticPowerMax(commander.getTacticPowerMax() + amount);
        commanderRepository.save(commander);

        // 진행 중인 런이 있으면 그 런의 전술력 현재치도 증가분만큼 동일하게 올려줌 — 없으면 다음 런 시작 시 새 tacticPowerMax로 초기화되므로 별도 처리 불필요
        ZoneRun activeRun = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS).orElse(null);
        int tacticPower;
        if (activeRun != null) {
            activeRun.setTacticPower(activeRun.getTacticPower() + amount);
            zoneRunRepository.save(activeRun);
            tacticPower = activeRun.getTacticPower();
        } else {
            tacticPower = commander.getTacticPowerMax();
        }

        return IncreaseTacticPowerMaxResponse.builder()
                .tacticPowerMax(commander.getTacticPowerMax())
                .tacticPower(tacticPower)
                .explorationPointRemain(commander.getExplorationPoint())
                .build();
    }

}
