package com.bk.sbs.service;

import com.bk.sbs.dto.ClaimAllDailyAchievementsResponse;
import com.bk.sbs.dto.ClaimDailyAchievementResponse;
import com.bk.sbs.dto.DailyAchievementStatusDto;
import com.bk.sbs.dto.GetDailyAchievementListResponse;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderDailyAchievementClaim;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.enums.ETreasureRewardType;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderDailyAchievementClaimRepository;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.VipSubscriptionRepository;
import com.bk.sbs.repository.ZoneCellClearLogRepository;
import com.bk.sbs.repository.ZoneRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// 일일(자정 UTC 리셋) 업적 조건 판정 + 수령 처리 — 배치/스케줄러 없이 매 요청마다 "오늘(UTC)" 구간을 계산해 라이브 판정(DailyBonus와 동일한 리셋 방식)
@Service
public class DailyAchievementService {

    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;
    private final ZoneCellClearLogRepository zoneCellClearLogRepository;
    private final ZoneRunRepository zoneRunRepository;
    private final CommanderDailyAchievementClaimRepository commanderDailyAchievementClaimRepository;
    private final VipSubscriptionRepository vipSubscriptionRepository;

    public DailyAchievementService(CommanderRepository commanderRepository, GameDataService gameDataService,
                                    ZoneCellClearLogRepository zoneCellClearLogRepository, ZoneRunRepository zoneRunRepository,
                                    CommanderDailyAchievementClaimRepository commanderDailyAchievementClaimRepository,
                                    VipSubscriptionRepository vipSubscriptionRepository) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.zoneRunRepository = zoneRunRepository;
        this.commanderDailyAchievementClaimRepository = commanderDailyAchievementClaimRepository;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
    }

    // 활성 VIP 여부 — IapService.isVipActive()와 동일 기준(서비스 간 커플링 없이 각자 보유)
    private boolean isVipActive(Long commanderId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCommanderId(commanderId);
        Instant expiry = sub.isPresent() ? sub.get().getVipExpiry() : null;
        return expiry != null && Instant.now().isBefore(expiry);
    }

    @Transactional(readOnly = true)
    public GetDailyAchievementListResponse getDailyAchievementStatusList(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<DailyAchievementStatusDto> statusList = gameDataService.getDailyAchievementList().stream()
                .map(entry -> DailyAchievementStatusDto.builder()
                        .achievementId(entry.achievementId)
                        .currentValue(computeDailyCurrentValue(commander, entry, dayStart, dayEnd))
                        .isClaimed(commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, entry.achievementId, today, false))
                        .isVipClaimed(commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, entry.achievementId, today, true))
                        .build())
                .collect(Collectors.toList());

        return GetDailyAchievementListResponse.builder().achievements(statusList).build();
    }

    // 완료+미수령 일일 업적이 하나라도 있는지만 필요한 호출용(레드닷 표시)
    @Transactional(readOnly = true)
    public boolean hasUnclaimedCompletedDailyAchievement(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId).orElse(null);
        if (commander == null) return false;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        boolean isVip = isVipActive(commanderId);

        for (GameDataService.DailyAchievementEntry entry : gameDataService.getDailyAchievementList()) {
            boolean isClaimed = commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, entry.achievementId, today, false);
            boolean isVipClaimed = isVip == true && commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, entry.achievementId, today, true);
            if (isClaimed == true && (isVip == false || isVipClaimed == true)) continue;

            int currentValue = computeDailyCurrentValue(commander, entry, dayStart, dayEnd);
            if (currentValue >= entry.threshold) return true;
        }
        return false;
    }

    @Transactional
    public ClaimDailyAchievementResponse claimDailyAchievement(Long commanderId, String achievementId, boolean claimVip) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        GameDataService.DailyAchievementEntry entry = gameDataService.getDailyAchievementList().stream()
                .filter(e -> e.achievementId.equals(achievementId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_NOT_FOUND));

        if (claimVip == true && isVipActive(commanderId) == false)
            throw new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_NOT_VIP);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, achievementId, today, claimVip) == true)
            throw new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_ALREADY_CLAIMED);

        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int currentValue = computeDailyCurrentValue(commander, entry, dayStart, dayEnd);
        if (currentValue < entry.threshold)
            throw new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_NOT_COMPLETED);

        int reward = claimVip == true ? entry.achievementPointRewardVip : entry.achievementPointReward;
        commander.setAchievementPoint(commander.getAchievementPoint() + reward);
        commanderRepository.save(commander);
        commanderDailyAchievementClaimRepository.save(new CommanderDailyAchievementClaim(commanderId, achievementId, today, claimVip));

        return ClaimDailyAchievementResponse.builder()
                .achievementId(achievementId)
                .achievementPointReward(reward)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // 완료+미수령 일일 업적을 전부 한 번에 수령 처리 — 일반 보상은 항상, VIP 보상은 VIP 활성 상태일 때만 같이 스윕
    @Transactional
    public ClaimAllDailyAchievementsResponse claimAllDailyAchievements(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        boolean isVip = isVipActive(commanderId);

        List<String> claimedIds = new ArrayList<>();
        int totalGranted = 0;

        for (GameDataService.DailyAchievementEntry entry : gameDataService.getDailyAchievementList()) {
            int currentValue = computeDailyCurrentValue(commander, entry, dayStart, dayEnd);
            if (currentValue < entry.threshold)
                continue;

            boolean normalAlreadyClaimed = commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, entry.achievementId, today, false);
            if (normalAlreadyClaimed == false) {
                commander.setAchievementPoint(commander.getAchievementPoint() + entry.achievementPointReward);
                commanderDailyAchievementClaimRepository.save(new CommanderDailyAchievementClaim(commanderId, entry.achievementId, today, false));
                claimedIds.add(entry.achievementId);
                totalGranted += entry.achievementPointReward;
            }

            if (isVip == true) {
                boolean vipAlreadyClaimed = commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, entry.achievementId, today, true);
                if (vipAlreadyClaimed == false) {
                    commander.setAchievementPoint(commander.getAchievementPoint() + entry.achievementPointRewardVip);
                    commanderDailyAchievementClaimRepository.save(new CommanderDailyAchievementClaim(commanderId, entry.achievementId, today, true));
                    totalGranted += entry.achievementPointRewardVip;
                }
            }
        }

        if (claimedIds.size() > 0 || totalGranted > 0)
            commanderRepository.save(commander);

        return ClaimAllDailyAchievementsResponse.builder()
                .claimedAchievementIds(claimedIds)
                .totalAchievementPointGranted(totalGranted)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // 조건 타입별 "오늘(UTC)" 진행도 계산 — CellClear/EventCell/ZoneClearTotal만 지원(나머지는 오늘 발생분을 셀 로그/타임스탬프가 없는 스냅샷이라 0 고정)
    private int computeDailyCurrentValue(Commander commander, GameDataService.DailyAchievementEntry entry, Instant dayStart, Instant dayEnd) {
        switch (entry.conditionType) {
            case CellClear:
                return (int) zoneCellClearLogRepository.countNormalCellClearByCommanderIdAndClearedAtBetween(commander.getId(), dayStart, dayEnd);
            case EventCell:
                ETreasureRewardType treasureRewardType = ETreasureRewardType.valueOf(entry.conditionParam);
                return (int) zoneCellClearLogRepository.countEventCellClearByCommanderIdAndTypeAndClearedAtBetween(commander.getId(), treasureRewardType, dayStart, dayEnd);
            case ZoneClearTotal:
                return (int) zoneRunRepository.countEscapedByCommanderIdAndEndedAtBetween(commander.getId(), dayStart, dayEnd);
            default:
                return 0;
        }
    }
}
