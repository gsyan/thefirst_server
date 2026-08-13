// 탐사 그리드 존 진행(ZoneRun) 서비스 — 셀 입장/클리어/탈출/포기, 탐험 포인트 정산, 지휘력 최대치 구매
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.Ship;
import com.bk.sbs.entity.ZoneCellClearLog;
import com.bk.sbs.entity.ZoneRun;
import com.bk.sbs.enums.EGridCellType;
import com.bk.sbs.enums.EZoneRunStatus;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.ShipRepository;
import com.bk.sbs.repository.ZoneCellClearLogRepository;
import com.bk.sbs.repository.ZoneRunRepository;
import com.bk.sbs.util.CommanderLevelUtil;
import com.bk.sbs.util.RewardCardSelector;
import com.bk.sbs.util.ZoneEnemyFleetGenerator;
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

    // explorationSeedBase는 엔티티에 저장되지 않고 world seed 그 자체(유저 무관 공통값) — 모든 유저가 같은 Zone에서 같은 적함대를 보도록 commanderId를 섞지 않음
    // CommanderService.getCommanderInfoDto와 동일 공식, 항상 함께 수정할 것
    @Value("${exploration.world-seed}")
    private int explorationWorldSeed;

    private final CommanderRepository commanderRepository;
    private final ZoneRunRepository zoneRunRepository;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final ShipRepository shipRepository;
    private final GameDataService gameDataService;
    private final ObjectMapper objectMapper;

    public ExplorationService(CommanderRepository commanderRepository, ZoneRunRepository zoneRunRepository,
                               ZoneCellClearLogRepository zoneCellClearLogRepository, ShipRepository shipRepository,
                               GameDataService gameDataService, ObjectMapper objectMapper) {
        this.commanderRepository = commanderRepository;
        this.zoneRunRepository = zoneRunRepository;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.shipRepository = shipRepository;
        this.gameDataService = gameDataService;
        this.objectMapper = objectMapper;
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

    // 체력 비율 범위(0~1), 함선 구성 일치, 직전 스냅샷 대비 증가폭(허용치: 회복 카드 효과 + 시간 여유값) 검증
    private void validateHealthSnapshot(Long commanderId, ZoneRun run, List<ShipHealthRatioInfoDto> reported) {
        if (reported == null || reported.isEmpty()) return;

        for (ShipHealthRatioInfoDto info : reported) {
            if (info.getHealthRatio() == null) continue;
            if (info.getHealthRatio() < HEALTH_RATIO_MIN || info.getHealthRatio() > HEALTH_RATIO_MAX)
                throw new BusinessException(ServerErrorCode.EXPLORATION_FLEET_HEALTH_INVALID);
        }

        validateFleetComposition(commanderId, reported);

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

    // 리포트된 함선이 실제로 이 커맨더 소유이고 서버가 아는 포지션과 일치하는지 검증(shipId 미포함 구버전 클라 하위호환 위해 0/null은 스킵)
    private void validateFleetComposition(Long commanderId, List<ShipHealthRatioInfoDto> reported) {
        for (ShipHealthRatioInfoDto info : reported) {
            if (info.getShipId() == null || info.getShipId() <= 0) continue;
            if (info.getPositionIndex() == null) continue;

            Ship ship = shipRepository.findByIdAndDeletedFalse(info.getShipId()).orElse(null);
            if (ship == null)
                throw new BusinessException(ServerErrorCode.EXPLORATION_FLEET_HEALTH_INVALID);
            if (ship.getFleet().getCommanderId().equals(commanderId) == false)
                throw new BusinessException(ServerErrorCode.EXPLORATION_FLEET_HEALTH_INVALID);
            if (ship.getPositionIndex() != info.getPositionIndex())
                throw new BusinessException(ServerErrorCode.EXPLORATION_FLEET_HEALTH_INVALID);
        }
    }

    // 직전 클리어 로그에서 선택 확정된 보상카드가 체력 즉시회복 계열이면 그 회복량을 허용 증가치로 반환
    private float getPendingHealthHealBonus(ZoneRun run) {
        List<ZoneCellClearLog> clearLogs = zoneCellClearLogRepository.findByZoneRunIdOrderByClearedAtAsc(run.getId());
        if (clearLogs.isEmpty()) return 0f;

        ZoneCellClearLog lastLog = clearLogs.get(clearLogs.size() - 1);
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
        return cardIds;
    }

    // 클라 CommonUtility.ComputeExplorationZoneSeed(zoneNumber, explorationSeedBase)와 동일 — 두 곳은 항상 함께 수정할 것
    private int computeZoneSeed(int zoneNumber, int explorationSeedBase) {
        return explorationSeedBase ^ (zoneNumber * 486187739);
    }

    // 유저 무관 공통 시드 — 모든 커맨더가 같은 Zone/셀에서 항상 같은 적함대를 계산해내야 하므로 commanderId를 섞지 않음
    private int computeZoneSeedShared(int zoneNumber) {
        return computeZoneSeed(zoneNumber, explorationWorldSeed);
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

    // enter-cell이 발급한 1회용 토큰을 검증 — 통과 시 즉시 무효화(재사용 방지). enter-cell 없이 clear-cell만 반복 호출하는 것을 막는 것이 목적
    private void validateAndConsumeChallengeToken(ZoneRun run, String requestToken, int cellRow, int cellCol) {
        String expectedCell = cellRow + "-" + cellCol;
        boolean tokenMatches = run.getActiveChallengeToken() != null
                && run.getActiveChallengeToken().equals(requestToken)
                && run.getActiveChallengeCell() != null
                && run.getActiveChallengeCell().equals(expectedCell);
        if (tokenMatches == false)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CHALLENGE_TOKEN_INVALID);

        long elapsedMillis = Instant.now().toEpochMilli() - run.getActiveChallengeIssuedAt().toEpochMilli();
        if (elapsedMillis < CHALLENGE_TOKEN_MIN_ELAPSED_MILLIS)
            throw new BusinessException(ServerErrorCode.EXPLORATION_CHALLENGE_TOKEN_INVALID);

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

        ZoneRun run;
        if (activeRunOpt.isPresent()) {
            run = activeRunOpt.get();
            if (run.getZoneNumber() != request.getZoneNumber())
                throw new BusinessException(ServerErrorCode.EXPLORATION_ANOTHER_ZONE_IN_PROGRESS);

            validateCellChallenge(zoneConfig, run.getCurrentRow(), run.getCurrentCol(), request.getCellRow(), request.getCellCol());
        } else {
            GridCellOverrideDto startCell = findCellByType(zoneConfig, EGridCellType.Start);
            if (startCell == null)
                throw new BusinessException(ServerErrorCode.EXPLORATION_START_CELL_NOT_CONFIGURED);

            validateCellChallenge(zoneConfig, startCell.getRow(), startCell.getCol(), request.getCellRow(), request.getCellCol());

            run = new ZoneRun(commanderId, request.getZoneNumber(), startCell.getRow(), startCell.getCol());
            run = zoneRunRepository.save(run);
        }

        // 이 셀에 대한 1회용 클리어 챌린지 토큰 발급 — clear-cell이 이 토큰 없이는 통과 못 하도록 함(enter 생략한 clear 반복 호출 차단)
        String challengeToken = java.util.UUID.randomUUID().toString();
        run.setActiveChallengeToken(challengeToken);
        run.setActiveChallengeCell(request.getCellRow() + "-" + request.getCellCol());
        run.setActiveChallengeIssuedAt(Instant.now());
        zoneRunRepository.save(run);

        int seed = computeZoneSeedShared(request.getZoneNumber());
        List<ZoneEnemyFleetGenerator.WaveResult> waves = ZoneEnemyFleetGenerator.generateWaves(
                zoneConfig, seed, request.getCellRow(), request.getCellCol(), gameDataService.getShipPresetList(), gameDataService);

        List<StageEnemyFleetSpawnConfigDto> enemyFleets = new ArrayList<>();
        for (int i = 0; i < waves.size(); i++) {
            ZoneEnemyFleetGenerator.WaveResult wave = waves.get(i);
            List<ShipInfoDto> ships = new ArrayList<>();
            for (ZoneEnemyFleetGenerator.ShipResult ship : wave.ships) {
                ships.add(ShipInfoDto.builder()
                        .shipPresetId(ship.presetId)
                        .isFront(ship.isFront)
                        .bodies(List.of(ship.modules))
                        .healthMultiplier(zoneConfig.getEnemyHealthMultiplier())
                        .attackMultiplier(zoneConfig.getEnemyAttackMultiplier())
                        .build());
            }
            enemyFleets.add(StageEnemyFleetSpawnConfigDto.builder()
                    .fleetIndex(i)
                    .positionIndex(0)
                    .fleetInfo(FleetInfoDto.builder().ships(ships).build())
                    .build());
        }

        return EnterExplorationCellResponse.builder()
                .zoneNumber(request.getZoneNumber())
                .cellRow(request.getCellRow())
                .cellCol(request.getCellCol())
                .enemyFleets(enemyFleets)
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

        if (isRevisit == false) {
            // 최초 클리어(보상 지급)에만 토큰을 요구 — enter-cell 없이 clear-cell 반복 호출로 무한 획득하는 것을 막는 지점
            validateAndConsumeChallengeToken(run, request.getChallengeToken(), request.getCellRow(), request.getCellCol());

            int seed = computeZoneSeedShared(request.getZoneNumber());
            List<ZoneEnemyFleetGenerator.WaveResult> waves = ZoneEnemyFleetGenerator.generateWaves(
                    zoneConfig, seed, request.getCellRow(), request.getCellCol(), gameDataService.getShipPresetList(), gameDataService);

            // 존 고정 보상값 적립 — 적 함대 성능(commandCost)과 무관, 웨이브에 함선이 있던 셀만 지급(빈 셀은 0)
            boolean hasEnemies = waves.stream().anyMatch(wave -> wave.ships.isEmpty() == false);
            pointsGained = hasEnemies ? zoneConfig.getExplorationPointReward() : 0;
            expGained    = hasEnemies ? zoneConfig.getCommanderExpReward()     : 0;

            // 탈출 셀은 보상카드 후보 생성 스킵 — 탈출 셀은 별도의 탈출 확정 흐름을 가짐
            GridCellOverrideDto escapeCell = findCellByType(zoneConfig, EGridCellType.Escape);
            boolean isEscapeCell = escapeCell != null && escapeCell.getRow() == request.getCellRow() && escapeCell.getCol() == request.getCellCol();
            rewardCardCandidates = (hasEnemies == true && isEscapeCell == false) ? rollRewardCardCandidates() : null;
        }

        validateHealthSnapshot(commanderId, run, request.getShipHealthRatios());

        run.setCurrentPosition(request.getCellRow(), request.getCellCol());
        run.setExplorationPointBanked(run.getExplorationPointBanked() + pointsGained);
        run.setCommanderExpBanked(run.getCommanderExpBanked() + expGained);
        run.setFleetHealthSnapshotJson(serializeHealthSnapshot(request.getShipHealthRatios(), run.getFleetHealthSnapshotJson()));
        zoneRunRepository.save(run);

        ZoneCellClearLog clearLog = new ZoneCellClearLog(run.getId(), request.getCellRow(), request.getCellCol());
        clearLog.setRewardCardCandidatesJson(serializeCardIdList(rewardCardCandidates));
        zoneCellClearLogRepository.save(clearLog);

        return ClearExplorationCellResponse.builder()
                .explorationPointGained(pointsGained)
                .expGained(expGained)
                .rewardCardCandidates(rewardCardCandidates)
                .build();
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

    // 탈출 성공/실패 공통 정산 결과 — 탐험 포인트/지휘관 경험치 확정 지급분
    private record RunSettlement(int pointPayout, int expPayout) {}

    // 탈출 성공/실패 공통 정산 — escapeExplorationZone(성공/실패)과 abandonZoneRun(실패 고정)이 공유
    private RunSettlement settleZoneRun(Commander commander, ZoneRun run, boolean isSuccess) {
        int pointPayout = isSuccess ? run.getExplorationPointBanked() : run.getExplorationPointBanked() / 2;
        int expPayout   = isSuccess ? run.getCommanderExpBanked()     : run.getCommanderExpBanked() / 2;

        commander.setExplorationPoint(commander.getExplorationPoint() + pointPayout);
        commander.setExp(commander.getExp() + expPayout);
        CommanderLevelUtil.autoLevelUpIfNeeded(commander, gameDataService);
        if (isSuccess && run.getZoneNumber() > commander.getHighestClearedZoneNumber())
            commander.setHighestClearedZoneNumber(run.getZoneNumber());

        run.setStatus(isSuccess ? EZoneRunStatus.ESCAPED : EZoneRunStatus.ABANDONED);
        run.setRewardClaimed(true);
        run.setEndedAt(Instant.now());

        commanderRepository.save(commander);
        zoneRunRepository.save(run);

        return new RunSettlement(pointPayout, expPayout);
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
}
