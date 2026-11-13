package com.bk.sbs.service;

import com.bk.sbs.dto.ClaimAllDailyAchievementsResponse;
import com.bk.sbs.dto.ClaimDailyAchievementResponse;
import com.bk.sbs.dto.DailyAchievementStatusDto;
import com.bk.sbs.dto.DailyQuestSettings;
import com.bk.sbs.dto.GetDailyAchievementListResponse;
import com.bk.sbs.enums.EAchievementConditionType;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderDailyAchievementClaim;
import com.bk.sbs.entity.VipSubscription;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
    private final DailyCounterService dailyCounterService;

    public DailyAchievementService(CommanderRepository commanderRepository, GameDataService gameDataService,
                                    ZoneCellClearLogRepository zoneCellClearLogRepository, ZoneRunRepository zoneRunRepository,
                                    CommanderDailyAchievementClaimRepository commanderDailyAchievementClaimRepository,
                                    VipSubscriptionRepository vipSubscriptionRepository,
                                    DailyCounterService dailyCounterService) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.zoneCellClearLogRepository = zoneCellClearLogRepository;
        this.zoneRunRepository = zoneRunRepository;
        this.commanderDailyAchievementClaimRepository = commanderDailyAchievementClaimRepository;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.dailyCounterService = dailyCounterService;
    }

    // 활성 VIP 여부 — IapService.isVipActive()와 동일 기준(서비스 간 커플링 없이 각자 보유)
    private boolean isVipActive(Long commanderId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCommanderId(commanderId);
        Instant expiry = sub.isPresent() ? sub.get().getVipExpiry() : null;
        return expiry != null && Instant.now().isBefore(expiry);
    }

    private static final String[] k_difficulties = { "easy", "normal", "hard" };

    // 연속 달성 보너스 규칙은 DataTableConfig.dailyQuest — JSON에 섹션이 없으면(재Export 전) 아래 기본값 사용
    private static final int k_defaultStreakPercentPerDay = 5;
    private static final int k_defaultStreakMaxDays = 10;

    private int getStreakPercentPerDay() {
        DailyQuestSettings settings = gameDataService.getDataTableConfig().getDailyQuest();
        if (settings == null || settings.getStreakPercentPerDay() == null) return k_defaultStreakPercentPerDay;
        return settings.getStreakPercentPerDay();
    }

    private int getStreakMaxDays() {
        DailyQuestSettings settings = gameDataService.getDataTableConfig().getDailyQuest();
        if (settings == null || settings.getStreakMaxDays() == null) return k_defaultStreakMaxDays;
        return settings.getStreakMaxDays();
    }

    // 오늘 보상에 적용되는 연속 일수 — 오늘 완료분은 포함하지 않음(하루 동안 값이 변하지 않게)
    // 마지막 완료일(lastDate, 오늘 이전) 이후 오늘까지 완료 없이 지나간 날 수만큼 연속 일수를 하루씩 깎음(0 미만 없음)
    private int getDecayedStreak(int streak, LocalDate lastDate, LocalDate today) {
        long daysSinceLast = ChronoUnit.DAYS.between(lastDate, today);
        long missedDays = Math.max(0L, daysSinceLast - 1);
        long decayed = streak - missedDays;
        return (int) Math.max(0L, decayed);
    }

    private int getAppliedStreakDays(Commander commander, LocalDate today) {
        LocalDate lastDate = commander.getDailyQuestStreakDate();
        int streak = commander.getDailyQuestStreak();
        int appliedStreak;
        if (lastDate == null) {
            appliedStreak = 0;
        } else if (lastDate.equals(today) == true) {
            appliedStreak = streak - 1;
        } else {
            appliedStreak = getDecayedStreak(streak, lastDate, today);
        }
        int maxDays = getStreakMaxDays();
        return Math.max(0, Math.min(appliedStreak, maxDays));
    }

    private int applyStreakBonus(int baseReward, int appliedStreakDays) {
        int percentPerDay = getStreakPercentPerDay();
        int bonusPercent = appliedStreakDays * percentPerDay;
        return baseReward * (100 + bonusPercent) / 100;
    }

    // 오늘의 3개를 모두 일반 보상까지 수령했으면 연속 일수 갱신(하루 1회) — 호출 전에 오늘 수령분이 저장돼 있어야 함
    private void updateStreakIfAllClaimed(Commander commander, LocalDate today) {
        if (today.equals(commander.getDailyQuestStreakDate()) == true) return;

        List<GameDataService.DailyAchievementEntry> todayEntries = getTodayDailyAchievements(commander.getId(), today);
        if (todayEntries.isEmpty() == true) return;

        Set<String> claimedIds = new HashSet<>();
        for (CommanderDailyAchievementClaim claim : commanderDailyAchievementClaimRepository.findByCommanderIdAndClaimDate(commander.getId(), today)) {
            if (claim.isVip() == false)
                claimedIds.add(claim.getDailyAchievementId());
        }
        for (GameDataService.DailyAchievementEntry entry : todayEntries) {
            if (claimedIds.contains(entry.achievementId) == false) return;
        }

        LocalDate lastDate = commander.getDailyQuestStreakDate();
        int decayedStreak = lastDate == null ? 0 : getDecayedStreak(commander.getDailyQuestStreak(), lastDate, today);
        int newStreak = decayedStreak + 1;
        commander.setDailyQuestStreak(newStreak);
        commander.setDailyQuestStreakDate(today);
    }

    // 인접한 시드(커맨더 id가 이웃)로 만든 java.util.Random은 첫 nextInt 결과가 거의 같아 이웃 유저가 같은 퀘스트를 받음 — 비트를 섞어 시드를 분산(splitmix64)
    private long mixSeed(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // 오늘(UTC)의 일일 업적 — 난이도별 풀에서 날짜+커맨더 시드로 1개씩 결정(저장 없이 항상 같은 결과). 풀이 비어 있는 난이도는 건너뜀
    // 레벨·보유 함선 등 하루 중 바뀔 수 있는 값은 추첨에 쓰지 않음 — 당장 수행할 수 없는 퀘스트가 뽑히는 것도 허용
    private List<GameDataService.DailyAchievementEntry> getTodayDailyAchievements(Long commanderId, LocalDate today) {
        List<GameDataService.DailyAchievementEntry> result = new ArrayList<>();
        for (String difficulty : k_difficulties) {
            List<GameDataService.DailyAchievementEntry> pool = gameDataService.getDailyAchievementList().stream()
                    .filter(e -> difficulty.equals(e.difficulty))
                    .sorted(Comparator.comparing(e -> e.achievementId))
                    .collect(Collectors.toList());
            if (pool.isEmpty() == true) continue;

            long daySeed = today.toEpochDay() * 1_000_003L;
            long commanderSeed = commanderId * 31L;
            long rawSeed = daySeed + commanderSeed + difficulty.hashCode();
            long seed = mixSeed(rawSeed);
            int pickedIndex = new Random(seed).nextInt(pool.size());
            result.add(pool.get(pickedIndex));
        }
        return result;
    }

    // 일일 업적 진행도 판정에 필요한 데이터를 요청당 한 번만 모아두는 스냅샷 — 항목 수만큼 반복 쿼리하는 대신
    // 조건 타입별 집계를 루프 진입 전에 1회씩만 조회
    private static class DailyAchievementProgressContext {
        long normalCellClearCount;
        long eventCellClearCount;
        long zoneClearTotalCount;
        Long commanderId;
        Instant dayStart;
        Instant dayEnd;
        Map<String, Long> dailyCounters; // counterType(EAchievementConditionType 이름) → 오늘 누적값
        Set<String> claimedAchievementIds;
        Set<String> vipClaimedAchievementIds;
    }

    private DailyAchievementProgressContext buildDailyProgressContext(Long commanderId, LocalDate today, Instant dayStart, Instant dayEnd) {
        DailyAchievementProgressContext context = new DailyAchievementProgressContext();

        context.normalCellClearCount = zoneCellClearLogRepository.countNormalCellClearByCommanderIdAndClearedAtBetween(commanderId, dayStart, dayEnd);
        context.eventCellClearCount = zoneCellClearLogRepository.countEventCellClearByCommanderIdAndClearedAtBetween(commanderId, dayStart, dayEnd);
        context.zoneClearTotalCount = zoneRunRepository.countEscapedByCommanderIdAndEndedAtBetween(commanderId, dayStart, dayEnd);
        context.commanderId = commanderId;
        context.dayStart = dayStart;
        context.dayEnd = dayEnd;
        context.dailyCounters = dailyCounterService.getCounters(commanderId, today);

        context.claimedAchievementIds = new HashSet<>();
        context.vipClaimedAchievementIds = new HashSet<>();
        for (CommanderDailyAchievementClaim claim : commanderDailyAchievementClaimRepository.findByCommanderIdAndClaimDate(commanderId, today)) {
            if (claim.isVip() == true)
                context.vipClaimedAchievementIds.add(claim.getDailyAchievementId());
            else
                context.claimedAchievementIds.add(claim.getDailyAchievementId());
        }

        return context;
    }

    @Transactional(readOnly = true)
    public GetDailyAchievementListResponse getDailyAchievementStatusList(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        DailyAchievementProgressContext context = buildDailyProgressContext(commanderId, today, dayStart, dayEnd);

        List<DailyAchievementStatusDto> statusList = getTodayDailyAchievements(commanderId, today).stream()
                .map(entry -> DailyAchievementStatusDto.builder()
                        .achievementId(entry.achievementId)
                        .currentValue(computeDailyCurrentValue(context, entry))
                        .isClaimed(context.claimedAchievementIds.contains(entry.achievementId))
                        .isVipClaimed(context.vipClaimedAchievementIds.contains(entry.achievementId))
                        .build())
                .collect(Collectors.toList());

        int appliedStreakDays = getAppliedStreakDays(commander, today);
        return GetDailyAchievementListResponse.builder().achievements(statusList).streakDays(appliedStreakDays).build();
    }

    // 완료+미수령 일일 업적이 하나라도 있는지만 필요한 호출용(레드닷 표시)
    @Transactional(readOnly = true)
    public boolean hasUnclaimedCompletedDailyAchievement(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId).orElse(null);
        if (commander == null) return false;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        DailyAchievementProgressContext context = buildDailyProgressContext(commanderId, today, dayStart, dayEnd);
        boolean isVip = isVipActive(commanderId);

        for (GameDataService.DailyAchievementEntry entry : getTodayDailyAchievements(commanderId, today)) {
            boolean isClaimed = context.claimedAchievementIds.contains(entry.achievementId);
            boolean isVipClaimed = isVip == true && context.vipClaimedAchievementIds.contains(entry.achievementId);
            if (isClaimed == true && (isVip == false || isVipClaimed == true)) continue;

            int currentValue = computeDailyCurrentValue(context, entry);
            if (currentValue >= entry.threshold) return true;
        }
        return false;
    }

    @Transactional
    public ClaimDailyAchievementResponse claimDailyAchievement(Long commanderId, String achievementId, boolean claimVip) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        GameDataService.DailyAchievementEntry entry = getTodayDailyAchievements(commanderId, today).stream()
                .filter(e -> e.achievementId.equals(achievementId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_NOT_FOUND));

        if (claimVip == true && isVipActive(commanderId) == false)
            throw new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_NOT_VIP);

        if (commanderDailyAchievementClaimRepository.existsByCommanderIdAndDailyAchievementIdAndClaimDateAndIsVip(commanderId, achievementId, today, claimVip) == true)
            throw new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_ALREADY_CLAIMED);

        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int currentValue = computeDailyCurrentValueDirect(commander, entry, dayStart, dayEnd);
        if (currentValue < entry.threshold)
            throw new BusinessException(ServerErrorCode.DAILY_ACHIEVEMENT_CLAIM_FAIL_NOT_COMPLETED);

        int baseReward = claimVip == true ? entry.achievementPointRewardVip : entry.achievementPointReward;
        int appliedStreakDays = getAppliedStreakDays(commander, today);
        int reward = applyStreakBonus(baseReward, appliedStreakDays);
        commander.setAchievementPoint(commander.getAchievementPoint() + reward);
        commanderDailyAchievementClaimRepository.save(new CommanderDailyAchievementClaim(commanderId, achievementId, today, claimVip));
        updateStreakIfAllClaimed(commander, today);
        commanderRepository.save(commander);

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
        DailyAchievementProgressContext context = buildDailyProgressContext(commanderId, today, dayStart, dayEnd);
        boolean isVip = isVipActive(commanderId);
        int appliedStreakDays = getAppliedStreakDays(commander, today);

        List<String> claimedIds = new ArrayList<>();
        int totalGranted = 0;

        for (GameDataService.DailyAchievementEntry entry : getTodayDailyAchievements(commanderId, today)) {
            int currentValue = computeDailyCurrentValue(context, entry);
            if (currentValue < entry.threshold)
                continue;

            boolean normalAlreadyClaimed = context.claimedAchievementIds.contains(entry.achievementId);
            if (normalAlreadyClaimed == false) {
                int normalReward = applyStreakBonus(entry.achievementPointReward, appliedStreakDays);
                commander.setAchievementPoint(commander.getAchievementPoint() + normalReward);
                commanderDailyAchievementClaimRepository.save(new CommanderDailyAchievementClaim(commanderId, entry.achievementId, today, false));
                claimedIds.add(entry.achievementId);
                totalGranted += normalReward;
            }

            if (isVip == true) {
                boolean vipAlreadyClaimed = context.vipClaimedAchievementIds.contains(entry.achievementId);
                if (vipAlreadyClaimed == false) {
                    int vipReward = applyStreakBonus(entry.achievementPointRewardVip, appliedStreakDays);
                    commander.setAchievementPoint(commander.getAchievementPoint() + vipReward);
                    commanderDailyAchievementClaimRepository.save(new CommanderDailyAchievementClaim(commanderId, entry.achievementId, today, true));
                    totalGranted += vipReward;
                }
            }
        }

        updateStreakIfAllClaimed(commander, today);
        commanderRepository.save(commander);

        return ClaimAllDailyAchievementsResponse.builder()
                .claimedAchievementIds(claimedIds)
                .totalAchievementPointGranted(totalGranted)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // 조건 타입별 "오늘(UTC)" 진행도 계산(배치 경로) — 셀/존 로그 기반 4종 외에는 commander_daily_counter 값을 사용(카운터가 없으면 0)
    private int computeDailyCurrentValue(DailyAchievementProgressContext context, GameDataService.DailyAchievementEntry entry) {
        switch (entry.conditionType) {
            case CellClear:
                return (int) context.normalCellClearCount;
            case EventCell:
                return (int) context.eventCellClearCount;
            case ZoneClearTotal:
                return (int) context.zoneClearTotalCount;
            case ZoneClearSpecific:
                return countEscapedSpecificZone(context.commanderId, entry, context.dayStart, context.dayEnd);
            default:
                Long counterValue = context.dailyCounters.get(entry.conditionType.name());
                long safeCounterValue = counterValue != null ? counterValue : 0L;
                return clampToInt(safeCounterValue);
        }
    }

    // 조건 타입별 "오늘(UTC)" 진행도 계산(단일 업적 경로) — claimDailyAchievement()는 항목 하나만 판정하므로 컨텍스트 전체를 모을 필요 없음
    private int computeDailyCurrentValueDirect(Commander commander, GameDataService.DailyAchievementEntry entry, Instant dayStart, Instant dayEnd) {
        switch (entry.conditionType) {
            case CellClear:
                return (int) zoneCellClearLogRepository.countNormalCellClearByCommanderIdAndClearedAtBetween(commander.getId(), dayStart, dayEnd);
            case EventCell:
                return (int) zoneCellClearLogRepository.countEventCellClearByCommanderIdAndClearedAtBetween(commander.getId(), dayStart, dayEnd);
            case ZoneClearTotal:
                return (int) zoneRunRepository.countEscapedByCommanderIdAndEndedAtBetween(commander.getId(), dayStart, dayEnd);
            case ZoneClearSpecific:
                return countEscapedSpecificZone(commander.getId(), entry, dayStart, dayEnd);
            default:
                LocalDate today = LocalDate.ofInstant(dayStart, ZoneOffset.UTC);
                long counterValue = dailyCounterService.getCounter(commander.getId(), today, entry.conditionType);
                return clampToInt(counterValue);
        }
    }

    // conditionParam = 존 번호. 숫자가 아니면 0(데이터 오류)
    private int countEscapedSpecificZone(Long commanderId, GameDataService.DailyAchievementEntry entry, Instant dayStart, Instant dayEnd) {
        int zoneNumber;
        try {
            zoneNumber = Integer.parseInt(entry.conditionParam.trim());
        } catch (Exception e) {
            return 0;
        }
        return (int) zoneRunRepository.countEscapedByCommanderIdAndZoneNumberAndEndedAtBetween(commanderId, zoneNumber, dayStart, dayEnd);
    }

    private int clampToInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
