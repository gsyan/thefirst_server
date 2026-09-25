package com.bk.sbs.service;

import com.bk.sbs.dto.AchievementStatusDto;
import com.bk.sbs.dto.ClaimAchievementResponse;
import com.bk.sbs.dto.ClaimAllAchievementsResponse;
import com.bk.sbs.dto.GetAchievementListResponse;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderAchievementClaim;
import com.bk.sbs.entity.CommanderAchievementReached;
import com.bk.sbs.entity.CommanderUnlockedHull;
import com.bk.sbs.entity.CommanderZoneFullClear;
import com.bk.sbs.entity.Fleet;
import com.bk.sbs.entity.Module;
import com.bk.sbs.entity.Ship;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.enums.EAchievementConditionType;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderAchievementClaimRepository;
import com.bk.sbs.repository.CommanderAchievementReachedRepository;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.CommanderUnlockedHullRepository;
import com.bk.sbs.repository.CommanderZoneFullClearRepository;
import com.bk.sbs.repository.FleetRepository;
import com.bk.sbs.repository.VipSubscriptionRepository;
import com.bk.sbs.repository.ZoneCellClearLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 업적 조건 판정 + 수령 처리 — 정의(GameDataService.AchievementEntry)는 데이터, 완료여부/수령상태는 여기서 매번 라이브 계산
@Service
@Slf4j
public class AchievementService {

    // 함체/모듈 동시보유 개수 업적은 활성 함대(fleetIndex=0) 하나만 기준(PvP용 등 다른 함대는 집계 제외)
    private static final int ACTIVE_FLEET_INDEX = 0;

    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final FleetRepository fleetRepository;
    private final CommanderAchievementClaimRepository commanderAchievementClaimRepository;
    private final CommanderAchievementReachedRepository commanderAchievementReachedRepository;
    private final CommanderUnlockedHullRepository commanderUnlockedHullRepository;
    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final CommanderZoneFullClearRepository commanderZoneFullClearRepository;

    public AchievementService(CommanderRepository commanderRepository, GameDataService gameDataService,
                               ZoneCellClearLogRepository zoneCellClearLogRepository, FleetRepository fleetRepository,
                               CommanderAchievementClaimRepository commanderAchievementClaimRepository,
                               CommanderAchievementReachedRepository commanderAchievementReachedRepository,
                               CommanderUnlockedHullRepository commanderUnlockedHullRepository,
                               VipSubscriptionRepository vipSubscriptionRepository,
                               CommanderZoneFullClearRepository commanderZoneFullClearRepository) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.fleetRepository = fleetRepository;
        this.commanderAchievementClaimRepository = commanderAchievementClaimRepository;
        this.commanderAchievementReachedRepository = commanderAchievementReachedRepository;
        this.commanderUnlockedHullRepository = commanderUnlockedHullRepository;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.commanderZoneFullClearRepository = commanderZoneFullClearRepository;
    }

    // 활성 VIP 여부 — IapService.isVipActive()와 동일 기준(서비스 간 커플링 없이 각자 보유)
    private boolean isVipActive(Long commanderId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCommanderId(commanderId);
        Instant expiry = sub.isPresent() ? sub.get().getVipExpiry() : null;
        return expiry != null && Instant.now().isBefore(expiry);
    }

    // 업적 진행도 판정에 필요한 데이터를 요청당 한 번만 모아두는 스냅샷 — 업적 항목 수(400여 개)만큼 반복 쿼리하는 대신
    // 조건 타입별 집계를 루프 진입 전에 1회씩만 조회해서, 쿼리 수가 업적 개수와 무관하게 고정되도록 함
    private static class AchievementProgressContext {
        long normalCellClearCount;
        long eventCellClearCount;
        Set<String> claimedAchievementIds;
        Set<String> vipClaimedAchievementIds;
        Set<String> reachedAchievementIds;
        Set<String> unlockedHullSubTypes;
        Map<Integer, Integer> hullAtOrAboveTierCounts;      // key: 티어 → 활성 함대에서 그 티어 이상인 함체 개수
        Map<String, Integer> moduleAtOrAboveTierCounts;     // key: "{EModuleType}_{티어}" → 그 카테고리에서 그 티어 이상인 모듈 개수
        Set<Integer> fullyClearedZoneNumbers;
    }

    private AchievementProgressContext buildProgressContext(Long commanderId, Fleet activeFleet) {
        AchievementProgressContext context = new AchievementProgressContext();

        context.normalCellClearCount = zoneCellClearLogRepository.countNormalCellClearByCommanderId(commanderId);
        context.eventCellClearCount = zoneCellClearLogRepository.countEventCellClearByCommanderId(commanderId);

        context.claimedAchievementIds = new HashSet<>();
        context.vipClaimedAchievementIds = new HashSet<>();
        for (CommanderAchievementClaim claim : commanderAchievementClaimRepository.findByCommanderId(commanderId)) {
            if (claim.isVip() == true)
                context.vipClaimedAchievementIds.add(claim.getAchievementId());
            else
                context.claimedAchievementIds.add(claim.getAchievementId());
        }

        context.reachedAchievementIds = new HashSet<>();
        for (CommanderAchievementReached reached : commanderAchievementReachedRepository.findByCommanderId(commanderId))
            context.reachedAchievementIds.add(reached.getAchievementId());

        context.unlockedHullSubTypes = new HashSet<>();
        for (CommanderUnlockedHull unlockedHull : commanderUnlockedHullRepository.findByCommanderId(commanderId))
            context.unlockedHullSubTypes.add(unlockedHull.getHullSubType());

        context.fullyClearedZoneNumbers = new HashSet<>();
        for (CommanderZoneFullClear fullClear : commanderZoneFullClearRepository.findByCommanderId(commanderId))
            context.fullyClearedZoneNumbers.add(fullClear.getZoneNumber());

        // 티어 이상 인정 — 함선/모듈 하나가 자기 티어 이하의 모든 티어 키에 1씩 기여함(티어6 1개 = 티어1~6 조건 각각 1개)
        context.hullAtOrAboveTierCounts = new HashMap<>();
        context.moduleAtOrAboveTierCounts = new HashMap<>();
        if (activeFleet != null && activeFleet.getShips() != null) {
            for (Ship ship : activeFleet.getShips()) {
                int hullTier = GameDataService.parseTierFromHullSubType(ship.getHullSubType());
                for (int tier = 1; tier <= hullTier; tier++)
                    context.hullAtOrAboveTierCounts.merge(tier, 1, Integer::sum);

                if (ship.getModules() == null) continue;
                for (Module module : ship.getModules()) {
                    int moduleTier = GameDataService.parseTierFromHullSubType(module.getModuleSubType());
                    for (int tier = 1; tier <= moduleTier; tier++) {
                        String moduleTierKey = module.getModuleType() + "_" + tier;
                        context.moduleAtOrAboveTierCounts.merge(moduleTierKey, 1, Integer::sum);
                    }
                }
            }
        }

        return context;
    }

    @Transactional(readOnly = true)
    public GetAchievementListResponse getAchievementStatusList(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        Fleet activeFleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, ACTIVE_FLEET_INDEX).orElse(null);
        AchievementProgressContext context = buildProgressContext(commanderId, activeFleet);

        List<AchievementStatusDto> statusList = gameDataService.getAchievementList().stream()
                .map(entry -> AchievementStatusDto.builder()
                        .achievementId(entry.achievementId)
                        .currentValue(computeCurrentValue(commander, context, entry))
                        .isClaimed(context.claimedAchievementIds.contains(entry.achievementId))
                        .isVipClaimed(context.vipClaimedAchievementIds.contains(entry.achievementId))
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
        AchievementProgressContext context = buildProgressContext(commanderId, activeFleet);
        boolean isVip = isVipActive(commanderId);

        for (GameDataService.AchievementEntry entry : gameDataService.getAchievementList()) {
            boolean isClaimed = context.claimedAchievementIds.contains(entry.achievementId);
            boolean isVipClaimed = isVip == true && context.vipClaimedAchievementIds.contains(entry.achievementId);
            if (isClaimed == true && (isVip == false || isVipClaimed == true)) continue;

            int currentValue = computeCurrentValue(commander, context, entry);
            if (currentValue >= entry.threshold) return true;
        }
        return false;
    }

    @Transactional
    public ClaimAchievementResponse claimAchievement(Long commanderId, String achievementId, boolean claimVip) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        GameDataService.AchievementEntry entry = gameDataService.getAchievementList().stream()
                .filter(e -> e.achievementId.equals(achievementId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_NOT_FOUND));

        if (claimVip == true && isVipActive(commanderId) == false)
            throw new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_NOT_VIP);

        if (commanderAchievementClaimRepository.existsByCommanderIdAndAchievementIdAndIsVip(commanderId, achievementId, claimVip) == true)
            throw new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_ALREADY_CLAIMED);

        Fleet activeFleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, ACTIVE_FLEET_INDEX).orElse(null);
        int currentValue = computeCurrentValueDirect(commander, activeFleet, entry);
        if (currentValue < entry.threshold)
            throw new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_NOT_COMPLETED);

        int reward = claimVip == true ? entry.achievementPointRewardVip : entry.achievementPointReward;
        commander.setAchievementPoint(commander.getAchievementPoint() + reward);
        commanderRepository.save(commander);
        commanderAchievementClaimRepository.save(new CommanderAchievementClaim(commanderId, achievementId, claimVip));

        return ClaimAchievementResponse.builder()
                .achievementId(achievementId)
                .achievementPointReward(reward)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // 완료+미수령 업적을 전부 한 번에 수령 처리 — 일반 보상은 항상, VIP 보상은 VIP 활성 상태일 때만 같이 스윕
    @Transactional
    public ClaimAllAchievementsResponse claimAllAchievements(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        Fleet activeFleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, ACTIVE_FLEET_INDEX).orElse(null);
        AchievementProgressContext context = buildProgressContext(commanderId, activeFleet);
        boolean isVip = isVipActive(commanderId);

        List<String> claimedIds = new ArrayList<>();
        int totalGranted = 0;

        for (GameDataService.AchievementEntry entry : gameDataService.getAchievementList()) {
            int currentValue = computeCurrentValue(commander, context, entry);
            if (currentValue < entry.threshold)
                continue;

            boolean normalAlreadyClaimed = context.claimedAchievementIds.contains(entry.achievementId);
            if (normalAlreadyClaimed == false) {
                commander.setAchievementPoint(commander.getAchievementPoint() + entry.achievementPointReward);
                commanderAchievementClaimRepository.save(new CommanderAchievementClaim(commanderId, entry.achievementId, false));
                claimedIds.add(entry.achievementId);
                totalGranted += entry.achievementPointReward;
            }

            if (isVip == true) {
                boolean vipAlreadyClaimed = context.vipClaimedAchievementIds.contains(entry.achievementId);
                if (vipAlreadyClaimed == false) {
                    commander.setAchievementPoint(commander.getAchievementPoint() + entry.achievementPointRewardVip);
                    commanderAchievementClaimRepository.save(new CommanderAchievementClaim(commanderId, entry.achievementId, true));
                    totalGranted += entry.achievementPointRewardVip;
                }
            }
        }

        if (claimedIds.size() > 0 || totalGranted > 0)
            commanderRepository.save(commander);

        return ClaimAllAchievementsResponse.builder()
                .claimedAchievementIds(claimedIds)
                .totalAchievementPointGranted(totalGranted)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // 조건 타입별 현재 진행도 계산(배치 경로) — buildProgressContext로 한 번만 모아둔 집계값을 순수 메모리 조회로만 읽음
    private int computeCurrentValue(Commander commander, AchievementProgressContext context, GameDataService.AchievementEntry entry) {
        switch (entry.conditionType) {
            case CellClear:
                return (int) context.normalCellClearCount;
            case EventCell:
                return (int) context.eventCellClearCount;
            case ZoneClearTotal:
                return commander.getHighestClearedZoneNumber();
            case ZoneClearSpecific:
                int requiredZoneNumber = Integer.parseInt(entry.conditionParam);
                return commander.getHighestClearedZoneNumber() >= requiredZoneNumber ? 1 : 0;
            case ZoneFullClear:
                int requiredFullClearZoneNumber = Integer.parseInt(entry.conditionParam);
                int batchResult = context.fullyClearedZoneNumbers.contains(requiredFullClearZoneNumber) ? 1 : 0;
                return batchResult;
            case CommanderLevel:
                return commander.getCommanderLevel();
            case CommandPower:
                return commander.getCommandPowerMax();
            case TacticPower:
                return commander.getTacticPowerMax();
            case ExplorationPointTotal:
                return commander.getExplorationPointEarnedTotal();
            case HullTierCount:
                int liveHullCount = context.hullAtOrAboveTierCounts.getOrDefault(Integer.parseInt(entry.conditionParam), 0);
                return applyReachedFloor(context, entry, liveHullCount);
            case ModuleTierCount:
                int liveModuleCount = context.moduleAtOrAboveTierCounts.getOrDefault(entry.conditionParam, 0);
                return applyReachedFloor(context, entry, liveModuleCount);
            case HullUnlocked:
                return context.unlockedHullSubTypes.contains(entry.conditionParam) ? 1 : 0;
            default:
                return 0;
        }
    }

    // 함대 구성 기반 업적은 한번 달성하면 장비를 바꿔도 완료 유지 — 달성 기록이 있으면 라이브 개수와 무관하게 최소 threshold로 취급
    private int applyReachedFloor(AchievementProgressContext context, GameDataService.AchievementEntry entry, int liveCount) {
        if (context.reachedAchievementIds.contains(entry.achievementId) == false) return liveCount;
        return Math.max(liveCount, entry.threshold);
    }

    // 조건 타입별 현재 진행도 계산(단일 업적 경로) — claimAchievement()는 항목 하나만 판정하므로 컨텍스트 전체를 모을 필요 없이 필요한 값만 직접 조회
    private int computeCurrentValueDirect(Commander commander, Fleet activeFleet, GameDataService.AchievementEntry entry) {
        switch (entry.conditionType) {
            case CellClear:
                return (int) zoneCellClearLogRepository.countNormalCellClearByCommanderId(commander.getId());
            case EventCell:
                return (int) zoneCellClearLogRepository.countEventCellClearByCommanderId(commander.getId());
            case ZoneClearTotal:
                return commander.getHighestClearedZoneNumber();
            case ZoneClearSpecific:
                int requiredZoneNumber = Integer.parseInt(entry.conditionParam);
                return commander.getHighestClearedZoneNumber() >= requiredZoneNumber ? 1 : 0;
            case ZoneFullClear:
                int requiredFullClearZoneNumber = Integer.parseInt(entry.conditionParam);
                boolean directExists = commanderZoneFullClearRepository.existsByCommanderIdAndZoneNumber(commander.getId(), requiredFullClearZoneNumber);
                return directExists ? 1 : 0;
            case CommanderLevel:
                return commander.getCommanderLevel();
            case CommandPower:
                return commander.getCommandPowerMax();
            case TacticPower:
                return commander.getTacticPowerMax();
            case ExplorationPointTotal:
                return commander.getExplorationPointEarnedTotal();
            case HullTierCount:
                int liveHullCount = countHullAtOrAboveTier(activeFleet, Integer.parseInt(entry.conditionParam));
                return applyReachedFloorDirect(commander, entry, liveHullCount);
            case ModuleTierCount:
                int liveModuleCount = countModuleAtOrAboveTier(activeFleet, entry.conditionParam);
                return applyReachedFloorDirect(commander, entry, liveModuleCount);
            case HullUnlocked:
                return commanderUnlockedHullRepository.existsByCommanderIdAndHullSubType(commander.getId(), entry.conditionParam) ? 1 : 0;
            default:
                return 0;
        }
    }

    // applyReachedFloor의 단일 조회 버전 — 컨텍스트 없이 달성 기록 1건만 직접 조회
    private int applyReachedFloorDirect(Commander commander, GameDataService.AchievementEntry entry, int liveCount) {
        boolean reached = commanderAchievementReachedRepository.existsByCommanderIdAndAchievementId(commander.getId(), entry.achievementId);
        if (reached == false) return liveCount;
        return Math.max(liveCount, entry.threshold);
    }

    // 함대 구성이 바뀐 직후 호출 — 티어 이상 조건을 지금 충족한 함대 구성 업적을 달성 기록으로 남겨, 이후 장비를 바꿔도 완료가 유지되게 함
    @Transactional
    public void recordReachedTierAchievements(Commander commander, Fleet fleet) {
        Set<String> reachedIds = new HashSet<>();
        for (CommanderAchievementReached reached : commanderAchievementReachedRepository.findByCommanderId(commander.getId()))
            reachedIds.add(reached.getAchievementId());

        for (GameDataService.AchievementEntry entry : gameDataService.getAchievementList()) {
            boolean isTierAchievement = entry.conditionType == EAchievementConditionType.HullTierCount
                    || entry.conditionType == EAchievementConditionType.ModuleTierCount;
            if (isTierAchievement == false) continue;
            if (reachedIds.contains(entry.achievementId) == true) continue;

            int liveCount = countLiveTierAchievement(fleet, entry);
            if (liveCount < entry.threshold) continue;

            commanderAchievementReachedRepository.save(new CommanderAchievementReached(commander.getId(), entry.achievementId));
        }
    }

    // 함대 구성 기반 업적의 현재 장착 기준 개수(달성 기록 미반영) — 업적 항목 수만큼 DB 조회하지 않도록 기록 판정 전용으로 분리
    private int countLiveTierAchievement(Fleet activeFleet, GameDataService.AchievementEntry entry) {
        if (entry.conditionType == EAchievementConditionType.HullTierCount)
            return countHullAtOrAboveTier(activeFleet, Integer.parseInt(entry.conditionParam));
        return countModuleAtOrAboveTier(activeFleet, entry.conditionParam);
    }

    private int countHullAtOrAboveTier(Fleet activeFleet, int tier) {
        if (activeFleet == null || activeFleet.getShips() == null) return 0;
        int count = 0;
        for (Ship ship : activeFleet.getShips()) {
            if (GameDataService.parseTierFromHullSubType(ship.getHullSubType()) >= tier)
                count++;
        }
        return count;
    }

    // conditionParam 형식: "{EModuleType 이름}_{티어}" (예: "beam_2") — 해당 카테고리에서 그 티어 이상인 모듈을 셈
    private int countModuleAtOrAboveTier(Fleet activeFleet, String conditionParam) {
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
                        && GameDataService.parseTierFromHullSubType(module.getModuleSubType()) >= targetTier)
                    count++;
            }
        }
        return count;
    }
}
