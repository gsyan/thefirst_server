// 탐사 그리드 존 진행(ZoneRun) 서비스 — 셀 입장/클리어/탈출/포기, 탐험 포인트 정산, 지휘력 최대치 구매
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.ZoneCellClearLog;
import com.bk.sbs.entity.ZoneRun;
import com.bk.sbs.enums.EGridCellType;
import com.bk.sbs.enums.EZoneRunStatus;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.ZoneCellClearLogRepository;
import com.bk.sbs.repository.ZoneRunRepository;
import com.bk.sbs.util.ZoneEnemyFleetGenerator;
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

    // 지휘력 최대치 구매 고정폭 — 임시 밸런스값(기획 확정 시 조정, Commander.commandPowerMax 기본값 300과 같은 성격)
    // 교환비: 탐험 포인트 100 -> 지휘력 10
    private static final int COMMAND_POWER_MAX_INCREASE = 10;
    private static final int COMMAND_POWER_MAX_COST      = 100;

    // explorationSeedBase는 엔티티에 저장되지 않고 world seed 그 자체(유저 무관 공통값) — 모든 유저가 같은 Zone에서 같은 적함대를 보도록 commanderId를 섞지 않음
    // CommanderService.getCommanderInfoDto와 동일 공식, 항상 함께 수정할 것
    @Value("${exploration.world-seed}")
    private int explorationWorldSeed;

    private final CommanderRepository commanderRepository;
    private final ZoneRunRepository zoneRunRepository;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final GameDataService gameDataService;

    public ExplorationService(CommanderRepository commanderRepository, ZoneRunRepository zoneRunRepository,
                               ZoneCellClearLogRepository zoneCellClearLogRepository, GameDataService gameDataService) {
        this.commanderRepository = commanderRepository;
        this.zoneRunRepository = zoneRunRepository;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.gameDataService = gameDataService;
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

        int seed = computeZoneSeedShared(request.getZoneNumber());
        List<ZoneEnemyFleetGenerator.WaveResult> waves = ZoneEnemyFleetGenerator.generateWaves(
                zoneConfig, seed, request.getCellRow(), request.getCellCol(), gameDataService.getShipPresetList());

        List<StageEnemyFleetSpawnConfigDto> enemyFleets = new ArrayList<>();
        for (int i = 0; i < waves.size(); i++) {
            ZoneEnemyFleetGenerator.WaveResult wave = waves.get(i);
            List<ShipInfoDto> ships = new ArrayList<>();
            for (ZoneEnemyFleetGenerator.ShipResult ship : wave.ships) {
                ships.add(ShipInfoDto.builder()
                        .shipPresetId(ship.presetId)
                        .isFront(ship.isFront)
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

        int seed = computeZoneSeedShared(request.getZoneNumber());
        List<ZoneEnemyFleetGenerator.WaveResult> waves = ZoneEnemyFleetGenerator.generateWaves(
                zoneConfig, seed, request.getCellRow(), request.getCellCol(), gameDataService.getShipPresetList());

        // 적 함대 총 성능포인트(commandCost 합) 만큼 탐험 포인트 적립 — 클라 자기신고 값을 쓰지 않고 서버가 직접 재계산
        int pointsGained = 0;
        for (ZoneEnemyFleetGenerator.WaveResult wave : waves) {
            for (ZoneEnemyFleetGenerator.ShipResult ship : wave.ships) {
                for (GameDataService.ShipPresetSummary preset : gameDataService.getShipPresetList()) {
                    if (preset.presetId.equals(ship.presetId)) {
                        pointsGained += preset.commandCost;
                        break;
                    }
                }
            }
        }

        run.setCurrentPosition(request.getCellRow(), request.getCellCol());
        run.setExplorationPointBanked(run.getExplorationPointBanked() + pointsGained);
        zoneRunRepository.save(run);
        zoneCellClearLogRepository.save(new ZoneCellClearLog(run.getId(), request.getCellRow(), request.getCellCol()));

        return ClearExplorationCellResponse.builder()
                .explorationPointGained(pointsGained)
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
                    .build();
        }

        ZoneRun run = activeRunOpt.get();
        List<String> clearedCells = zoneCellClearLogRepository.findByZoneRunIdOrderByClearedAtAsc(run.getId())
                .stream()
                .map(ZoneCellClearLog::getCell)
                .toList();

        return GetActiveZoneRunProgressResponse.builder()
                .zoneNumber(run.getZoneNumber())
                .clearedCells(clearedCells)
                .explorationPointBanked(run.getExplorationPointBanked())
                .build();
    }

    // 탈출 성공/실패 공통 정산 — escapeExplorationZone(성공/실패)과 abandonZoneRun(실패 고정)이 공유
    private int settleZoneRun(Commander commander, ZoneRun run, boolean isSuccess) {
        int payout = isSuccess ? run.getExplorationPointBanked() : run.getExplorationPointBanked() / 2;

        commander.setExplorationPoint(commander.getExplorationPoint() + payout);
        if (isSuccess && run.getZoneNumber() > commander.getHighestClearedZoneNumber())
            commander.setHighestClearedZoneNumber(run.getZoneNumber());

        run.setStatus(isSuccess ? EZoneRunStatus.ESCAPED : EZoneRunStatus.ABANDONED);
        run.setRewardClaimed(true);
        run.setEndedAt(Instant.now());

        commanderRepository.save(commander);
        zoneRunRepository.save(run);

        return payout;
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

        int payout = settleZoneRun(commander, run, isSuccess);

        return EscapeExplorationZoneResponse.builder()
                .explorationPointGained(payout)
                .explorationPointRemain(commander.getExplorationPoint())
                .build();
    }

    @Transactional
    public AbandonZoneRunResponse abandonZoneRun(Long commanderId) {
        ZoneRun run = zoneRunRepository.findByCommanderIdAndStatus(commanderId, EZoneRunStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_NO_ACTIVE_RUN));

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        int payout = settleZoneRun(commander, run, false);

        return AbandonZoneRunResponse.builder()
                .explorationPointGained(payout)
                .explorationPointRemain(commander.getExplorationPoint())
                .build();
    }

    @Transactional
    public IncreaseCommandPowerMaxResponse increaseCommandPowerMax(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.EXPLORATION_FAIL_COMMANDER_NOT_FOUND));

        if (commander.getExplorationPoint() < COMMAND_POWER_MAX_COST)
            throw new BusinessException(ServerErrorCode.EXPLORATION_POINT_INSUFFICIENT);

        commander.setExplorationPoint(commander.getExplorationPoint() - COMMAND_POWER_MAX_COST);
        commander.setCommandPowerMax(commander.getCommandPowerMax() + COMMAND_POWER_MAX_INCREASE);
        commanderRepository.save(commander);

        return IncreaseCommandPowerMaxResponse.builder()
                .commandPowerMax(commander.getCommandPowerMax())
                .explorationPointRemain(commander.getExplorationPoint())
                .build();
    }
}
