package com.bk.sbs.service;

import com.bk.sbs.dto.AchievementStatusDto;
import com.bk.sbs.dto.ClaimAchievementResponse;
import com.bk.sbs.dto.ClaimAllAchievementsResponse;
import com.bk.sbs.dto.GetAchievementListResponse;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderAchievementClaim;
import com.bk.sbs.entity.Fleet;
import com.bk.sbs.entity.Module;
import com.bk.sbs.entity.Ship;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.enums.ETreasureRewardType;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderAchievementClaimRepository;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.CommanderUnlockedHullRepository;
import com.bk.sbs.repository.FleetRepository;
import com.bk.sbs.repository.ZoneCellClearLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// 업적 조건 판정 + 수령 처리 — 정의(GameDataService.AchievementEntry)는 데이터, 완료여부/수령상태는 여기서 매번 라이브 계산
@Service
public class AchievementService {

    // 함체/모듈 동시보유 개수 업적은 활성 함대(fleetIndex=0) 하나만 기준(PvP용 등 다른 함대는 집계 제외)
    private static final int ACTIVE_FLEET_INDEX = 0;

    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final FleetRepository fleetRepository;
    private final CommanderAchievementClaimRepository commanderAchievementClaimRepository;
    private final CommanderUnlockedHullRepository commanderUnlockedHullRepository;

    public AchievementService(CommanderRepository commanderRepository, GameDataService gameDataService,
                               ZoneCellClearLogRepository zoneCellClearLogRepository, FleetRepository fleetRepository,
                               CommanderAchievementClaimRepository commanderAchievementClaimRepository,
                               CommanderUnlockedHullRepository commanderUnlockedHullRepository) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.fleetRepository = fleetRepository;
        this.commanderAchievementClaimRepository = commanderAchievementClaimRepository;
        this.commanderUnlockedHullRepository = commanderUnlockedHullRepository;
    }

    @Transactional(readOnly = true)
    public GetAchievementListResponse getAchievementStatusList(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        Fleet activeFleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, ACTIVE_FLEET_INDEX).orElse(null);

        List<AchievementStatusDto> statusList = gameDataService.getAchievementList().stream()
                .map(entry -> AchievementStatusDto.builder()
                        .achievementId(entry.achievementId)
                        .currentValue(computeCurrentValue(commander, activeFleet, entry))
                        .isClaimed(commanderAchievementClaimRepository.existsByCommanderIdAndAchievementId(commanderId, entry.achievementId))
                        .build())
                .collect(Collectors.toList());

        return GetAchievementListResponse.builder().achievements(statusList).build();
    }

    // 완료+미수령 업적이 하나라도 있는지만 필요한 호출용(레드닷 표시) — 전체 목록 DTO 생성 없이 발견 즉시 리턴
    @Transactional(readOnly = true)
    public boolean hasUnclaimedCompletedAchievement(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId).orElse(null);
        if (commander == null) return false;

        Fleet activeFleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, ACTIVE_FLEET_INDEX).orElse(null);

        for (GameDataService.AchievementEntry entry : gameDataService.getAchievementList()) {
            boolean isClaimed = commanderAchievementClaimRepository.existsByCommanderIdAndAchievementId(commanderId, entry.achievementId);
            if (isClaimed == true) continue;

            int currentValue = computeCurrentValue(commander, activeFleet, entry);
            if (currentValue >= entry.threshold) return true;
        }
        return false;
    }

    @Transactional
    public ClaimAchievementResponse claimAchievement(Long commanderId, String achievementId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        GameDataService.AchievementEntry entry = gameDataService.getAchievementList().stream()
                .filter(e -> e.achievementId.equals(achievementId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_NOT_FOUND));

        if (commanderAchievementClaimRepository.existsByCommanderIdAndAchievementId(commanderId, achievementId) == true)
            throw new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_ALREADY_CLAIMED);

        Fleet activeFleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, ACTIVE_FLEET_INDEX).orElse(null);
        int currentValue = computeCurrentValue(commander, activeFleet, entry);
        if (currentValue < entry.threshold)
            throw new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_NOT_COMPLETED);

        commander.setAchievementPoint(commander.getAchievementPoint() + entry.achievementPointReward);
        commanderRepository.save(commander);
        commanderAchievementClaimRepository.save(new CommanderAchievementClaim(commanderId, achievementId));

        return ClaimAchievementResponse.builder()
                .achievementId(achievementId)
                .achievementPointReward(entry.achievementPointReward)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // 완료+미수령 업적을 전부 한 번에 수령 처리 — 개별 claimAchievement와 동일한 조건/계산 로직 재사용
    @Transactional
    public ClaimAllAchievementsResponse claimAllAchievements(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        Fleet activeFleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, ACTIVE_FLEET_INDEX).orElse(null);

        List<String> claimedIds = new ArrayList<>();
        int totalGranted = 0;

        for (GameDataService.AchievementEntry entry : gameDataService.getAchievementList()) {
            if (commanderAchievementClaimRepository.existsByCommanderIdAndAchievementId(commanderId, entry.achievementId) == true)
                continue;

            int currentValue = computeCurrentValue(commander, activeFleet, entry);
            if (currentValue < entry.threshold)
                continue;

            commander.setAchievementPoint(commander.getAchievementPoint() + entry.achievementPointReward);
            commanderAchievementClaimRepository.save(new CommanderAchievementClaim(commanderId, entry.achievementId));
            claimedIds.add(entry.achievementId);
            totalGranted += entry.achievementPointReward;
        }

        if (claimedIds.size() > 0)
            commanderRepository.save(commander);

        return ClaimAllAchievementsResponse.builder()
                .claimedAchievementIds(claimedIds)
                .totalAchievementPointGranted(totalGranted)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // 조건 타입별 현재 진행도 계산 — activeFleet은 함대 미보유(신규 계정 등)면 null일 수 있어 함체/모듈 카운트는 0 처리
    private int computeCurrentValue(Commander commander, Fleet activeFleet, GameDataService.AchievementEntry entry) {
        switch (entry.conditionType) {
            case CellClear:
                return (int) zoneCellClearLogRepository.countNormalCellClearByCommanderId(commander.getId());
            case EventCell:
                ETreasureRewardType treasureRewardType = ETreasureRewardType.valueOf(entry.conditionParam);
                return (int) zoneCellClearLogRepository.countEventCellClearByCommanderIdAndType(commander.getId(), treasureRewardType);
            case ZoneClearTotal:
                return commander.getHighestClearedZoneNumber();
            case ZoneClearSpecific:
                int requiredZoneNumber = Integer.parseInt(entry.conditionParam);
                return commander.getHighestClearedZoneNumber() >= requiredZoneNumber ? 1 : 0;
            case CommanderLevel:
                return commander.getCommanderLevel();
            case CommandPower:
                return commander.getCommandPowerMax();
            case TacticPower:
                return commander.getTacticPowerMax();
            case ExplorationPointTotal:
                return commander.getExplorationPointEarnedTotal();
            case HullTierCount:
                return countHullTier(activeFleet, Integer.parseInt(entry.conditionParam));
            case ModuleTierCount:
                return countModuleTier(activeFleet, entry.conditionParam);
            case HullUnlocked:
                return commanderUnlockedHullRepository.existsByCommanderIdAndHullSubType(commander.getId(), entry.conditionParam) ? 1 : 0;
            default:
                return 0;
        }
    }

    private int countHullTier(Fleet activeFleet, int tier) {
        if (activeFleet == null || activeFleet.getShips() == null) return 0;
        int count = 0;
        for (Ship ship : activeFleet.getShips()) {
            if (GameDataService.parseTierFromHullSubType(ship.getHullSubType()) == tier)
                count++;
        }
        return count;
    }

    // conditionParam 형식: "{EModuleType 이름}_{티어}" (예: "beam_2")
    private int countModuleTier(Fleet activeFleet, String conditionParam) {
        if (activeFleet == null || activeFleet.getShips() == null) return 0;
        int separatorIndex = conditionParam.lastIndexOf('_');
        if (separatorIndex < 0) return 0;
        EModuleType targetModuleType = EModuleType.valueOf(conditionParam.substring(0, separatorIndex));
        int targetTier = Integer.parseInt(conditionParam.substring(separatorIndex + 1));

        int count = 0;
        for (Ship ship : activeFleet.getShips()) {
            if (ship.getModules() == null) continue;
            for (Module module : ship.getModules()) {
                if (module.getModuleType() == targetModuleType
                        && GameDataService.parseTierFromHullSubType(module.getModuleSubType()) == targetTier)
                    count++;
            }
        }
        return count;
    }
}
