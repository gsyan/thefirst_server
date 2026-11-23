package com.bk.sbs.service;

import com.bk.sbs.dto.AchievementStatusDto;
import com.bk.sbs.dto.ClaimAchievementResponse;
import com.bk.sbs.dto.ClaimAllAchievementsResponse;
import com.bk.sbs.dto.GetAchievementListResponse;
import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderAchievementClaim;
import com.bk.sbs.entity.CommanderArchetypeModuleUpgrade;
import com.bk.sbs.entity.CommanderUnlockedHull;
import com.bk.sbs.entity.CommanderZoneFullClear;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderAchievementClaimRepository;
import com.bk.sbs.repository.CommanderArchetypeModuleUpgradeRepository;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.CommanderUnlockedHullRepository;
import com.bk.sbs.repository.CommanderZoneFullClearRepository;
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

    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final CommanderAchievementClaimRepository commanderAchievementClaimRepository;
    private final CommanderArchetypeModuleUpgradeRepository commanderArchetypeModuleUpgradeRepository;
    private final CommanderUnlockedHullRepository commanderUnlockedHullRepository;
    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final CommanderZoneFullClearRepository commanderZoneFullClearRepository;
    private final DailyAchievementService dailyAchievementService;
    private final HullArchetypeProgressService hullArchetypeProgressService;

    public AchievementService(CommanderRepository commanderRepository, GameDataService gameDataService,
                               ZoneCellClearLogRepository zoneCellClearLogRepository,
                               CommanderAchievementClaimRepository commanderAchievementClaimRepository,
                               CommanderArchetypeModuleUpgradeRepository commanderArchetypeModuleUpgradeRepository,
                               CommanderUnlockedHullRepository commanderUnlockedHullRepository,
                               VipSubscriptionRepository vipSubscriptionRepository,
                               CommanderZoneFullClearRepository commanderZoneFullClearRepository,
                               DailyAchievementService dailyAchievementService,
                               HullArchetypeProgressService hullArchetypeProgressService) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.commanderAchievementClaimRepository = commanderAchievementClaimRepository;
        this.commanderArchetypeModuleUpgradeRepository = commanderArchetypeModuleUpgradeRepository;
        this.commanderUnlockedHullRepository = commanderUnlockedHullRepository;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.commanderZoneFullClearRepository = commanderZoneFullClearRepository;
        this.dailyAchievementService = dailyAchievementService;
        this.hullArchetypeProgressService = hullArchetypeProgressService;
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
        Set<String> unlockedHullArchetypes;
        Map<String, Integer> completedPassiveStageByArchetype;  // key: 함체 아키타입 → 완료한 패시브 단계
        Map<String, Integer> reinforceCountByArchetype;         // key: 함체 아키타입 → 강화 횟수 합계(함체+모듈 전체 슬롯)
        Set<Integer> fullyClearedZoneNumbers;
    }

    private AchievementProgressContext buildProgressContext(Long commanderId) {
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

        context.unlockedHullArchetypes = getUnlockedHullArchetypes(commanderId);
        context.completedPassiveStageByArchetype = hullArchetypeProgressService.getCompletedStageByArchetype(commanderId);
        context.reinforceCountByArchetype = getReinforceCountByArchetype(commanderId);

        context.fullyClearedZoneNumbers = new HashSet<>();
        for (CommanderZoneFullClear fullClear : commanderZoneFullClearRepository.findByCommanderId(commanderId))
            context.fullyClearedZoneNumbers.add(fullClear.getZoneNumber());

        return context;
    }

    // 언락 기록(hullSubType)을 함체 아키타입으로 변환 — 현재 데이터테이블에 없는 함체의 기록은 제외
    private Set<String> getUnlockedHullArchetypes(Long commanderId) {
        Set<String> archetypes = new HashSet<>();
        for (CommanderUnlockedHull unlockedHull : commanderUnlockedHullRepository.findByCommanderId(commanderId)) {
            ModuleData hullData = gameDataService.getHullModuleData(unlockedHull.getHullSubType());
            if (hullData == null || hullData.getHullArchetype() == null) continue;
            archetypes.add(hullData.getHullArchetype());
        }
        return archetypes;
    }

    private Map<String, Integer> getReinforceCountByArchetype(Long commanderId) {
        Map<String, Integer> counts = new HashMap<>();
        for (CommanderArchetypeModuleUpgrade upgrade : commanderArchetypeModuleUpgradeRepository.findByCommanderId(commanderId))
            counts.merge(upgrade.getHullArchetype(), upgrade.getLevel(), Integer::sum);
        return counts;
    }

    @Transactional(readOnly = true)
    public GetAchievementListResponse getAchievementStatusList(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        AchievementProgressContext context = buildProgressContext(commanderId);

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

        AchievementProgressContext context = buildProgressContext(commanderId);
        boolean isVip = isVipActive(commanderId);

        for (GameDataService.AchievementEntry entry : gameDataService.getAchievementList()) {
            boolean isClaimed = context.claimedAchievementIds.contains(entry.achievementId);
            boolean isVipClaimed = isVip == true && context.vipClaimedAchievementIds.contains(entry.achievementId);
            if (isClaimed == true && (isVip == false || isVipClaimed == true)) continue;

            int currentValue = computeCurrentValue(commander, context, entry);
            if (currentValue >= entry.threshold) return true;
        }

        // 클라 레드닷(UIPanelAchievement.RefreshUnclaimedIndicators)이 일일퀘스트 미수령까지 합산하므로 서버 값도 같은 기준으로 맞춤
        return dailyAchievementService.hasUnclaimedCompletedDailyAchievement(commanderId);
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

        int currentValue = computeCurrentValueDirect(commander, entry);
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

        AchievementProgressContext context = buildProgressContext(commanderId);
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
                return gameDataService.getCommandPowerMax(commander.getCommanderLevel());
            case MineralTotal:
                return commander.getMineralEarnedTotal();
            case HullPassiveStage:
                return context.completedPassiveStageByArchetype.getOrDefault(entry.conditionParam, 0);
            case HullReinforceTotal:
                return context.reinforceCountByArchetype.getOrDefault(entry.conditionParam, 0);
            case HullUnlocked:
                return context.unlockedHullArchetypes.contains(entry.conditionParam) ? 1 : 0;
            default:
                return 0;
        }
    }

    // 조건 타입별 현재 진행도 계산(단일 업적 경로) — claimAchievement()는 항목 하나만 판정하므로 컨텍스트 전체를 모을 필요 없이 필요한 값만 직접 조회
    private int computeCurrentValueDirect(Commander commander, GameDataService.AchievementEntry entry) {
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
                return gameDataService.getCommandPowerMax(commander.getCommanderLevel());
            case MineralTotal:
                return commander.getMineralEarnedTotal();
            case HullPassiveStage:
                Map<String, Integer> completedStages = hullArchetypeProgressService.getCompletedStageByArchetype(commander.getId());
                return completedStages.getOrDefault(entry.conditionParam, 0);
            case HullReinforceTotal:
                Map<String, Integer> reinforceCounts = getReinforceCountByArchetype(commander.getId());
                return reinforceCounts.getOrDefault(entry.conditionParam, 0);
            case HullUnlocked:
                Set<String> unlockedArchetypes = getUnlockedHullArchetypes(commander.getId());
                return unlockedArchetypes.contains(entry.conditionParam) ? 1 : 0;
            default:
                return 0;
        }
    }
}
