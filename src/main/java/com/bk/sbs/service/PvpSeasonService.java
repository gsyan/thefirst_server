package com.bk.sbs.service;

import com.bk.sbs.config.DataTablePvpSeason;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.PvpRecord;
import com.bk.sbs.entity.PvpSeason;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.PvpRecordRepository;
import com.bk.sbs.repository.PvpSeasonRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PvpSeasonService {

    private final PvpSeasonRepository pvpSeasonRepository;
    private final PvpRecordRepository pvpRecordRepository;
    private final CommanderRepository commanderRepository;
    private final RedisService redisService;
    private final GameDataService gameDataService;

    public PvpSeasonService(PvpSeasonRepository pvpSeasonRepository,
                            PvpRecordRepository pvpRecordRepository,
                            CommanderRepository commanderRepository,
                            RedisService redisService,
                            GameDataService gameDataService) {
        this.pvpSeasonRepository = pvpSeasonRepository;
        this.pvpRecordRepository = pvpRecordRepository;
        this.commanderRepository = commanderRepository;
        this.redisService = redisService;
        this.gameDataService = gameDataService;
    }

    // ── 조회 ───────────────────────────────────────────────────────────────

    public Optional<PvpSeason> getCurrentSeason() {
        return pvpSeasonRepository.findTopByOrderBySeasonNumberDesc();
    }

    // ── 수동 시즌 설정 (진행 중에도 변경 가능) ─────────────────────────────

    @Transactional
    public PvpSeason setSeasonManual(int seasonNumber, Instant startTime, Instant endTime) {
        PvpSeason season = pvpSeasonRepository.findById(seasonNumber)
                .orElse(new PvpSeason());

        season.setSeasonNumber(seasonNumber);
        season.setStartTime(startTime);
        season.setEndTime(endTime);
        pvpSeasonRepository.save(season);

        // Redis 업데이트
        redisService.setPvpSeasonInfo(seasonNumber, startTime.toString(), endTime.toString());

        log.info("PVP 시즌 설정 완료: 시즌={} {} ~ {}", seasonNumber, startTime, endTime);
        return season;
    }

    // ── 시즌 종료 처리 (보상 지급 + 점수 리셋 + 다음 시즌 자동 시작) ────────

    @Transactional
    public void endSeasonAndStartNext(PvpSeason season) {
        if (season.isRewardDistributed()) {
            log.info("시즌 {} 이미 종료 처리됨, 스킵", season.getSeasonNumber());
            return;
        }

        distributeSeasonReward(season);
        resetSeasonScores();

        int nextSeasonNumber = season.getSeasonNumber() + 1;
        // 현재 시즌 종료 시각을 다음 시즌 시작으로 그대로 이어붙여 공백 없이 연결
        Instant nextStart = season.getEndTime();
        Instant nextEnd = calcWeeklySeasonEnd(nextStart);

        setSeasonManual(nextSeasonNumber, nextStart, nextEnd);
        log.info("다음 시즌 자동 시작: 시즌 {}", nextSeasonNumber);
    }

    // ── 보상 지급 ──────────────────────────────────────────────────────────

    @Transactional
    public void distributeSeasonReward(PvpSeason season) {
        // 접속 시 개별 지급 방식 — 여기서는 종료 마킹만 처리
        season.setRewardDistributed(true);
        pvpSeasonRepository.save(season);
        log.info("시즌 {} 종료 마킹 완료 (보상은 접속 시 개별 지급)", season.getSeasonNumber());
    }

    // ── 접속 시 미수령 시즌 보상 지급 ────────────────────────────────────

    @Transactional
    public int claimPendingSeasonReward(long commanderId) {
        Optional<PvpSeason> seasonOpt = pvpSeasonRepository.findTopByOrderBySeasonNumberDesc();
        if (seasonOpt.isPresent() == false) return 0;

        PvpSeason season = seasonOpt.get();
        if (season.isRewardDistributed() == false) return 0;

        Optional<PvpRecord> recordOpt = pvpRecordRepository.findByCommanderId(commanderId);
        if (recordOpt.isPresent() == false) return 0;

        PvpRecord record = recordOpt.get();
        if (record.getLastRewardedSeason() >= season.getSeasonNumber()) return 0;

        DataTablePvpSeason pvpSeasonTable = gameDataService.getDataTablePvpSeason();
        int reward = pvpSeasonTable.getSeasonReward(record.getScore());

        // 보상 수령 처리 (reward <= 0 이어도 lastRewardedSeason은 갱신)
        if (reward > 0) {
            // season.endTime == 다음 시즌 시작 시각 → 다음 시즌 종료일까지를 만료일로 설정
            Instant rewardExpiry = calcWeeklySeasonEnd(season.getEndTime());

            Commander commander = commanderRepository.findById(commanderId).orElse(null);
            if (commander == null) return 0;

            commander.setPvpPoint(commander.getPvpPoint() + reward);
            commander.setPvpPointMaxGot(commander.getPvpPointMaxGot() + reward);
            commander.setPvpPointExpiry(rewardExpiry);
            commander.setPvpPointSeasonRef(season.getSeasonNumber());
            commanderRepository.save(commander);
        }

        record.setLastRewardedSeason(season.getSeasonNumber());
        pvpRecordRepository.save(record);
        log.info("시즌 {} 보상 지급: commanderId={}, reward={}", season.getSeasonNumber(), commanderId, reward);
        return reward;
    }

    // ── 티어 기반 점수 리셋 ────────────────────────────────────────────────

    @Transactional
    public void resetSeasonScores() {
        DataTablePvpSeason pvpSeasonTable = gameDataService.getDataTablePvpSeason();
        List<PvpRecord> records = pvpRecordRepository.findAll();

        for (PvpRecord record : records) {
            int resetScore = pvpSeasonTable.getResetScore(record.getScore());
            record.setScore(resetScore);
            record.setWins(0);
            record.setLosses(0);
            pvpRecordRepository.save(record);
            redisService.setPvpScore(record.getCommanderId(), resetScore);
        }

        redisService.snapshotPvpRanking();
        log.info("시즌 점수 리셋 완료: {}건", records.size());
    }

    // ── 서버 시작 시 시즌 초기화 ──────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initSeasonOnStartup() {
        Optional<PvpSeason> currentOpt = getCurrentSeason();

        if (currentOpt.isPresent() == false) {
            // 이번 주 월요일 00:00 UTC ~ 다음 주 월요일 00:00 UTC 기준 시즌 1 생성
            Instant start = calcWeeklySeasonStart(Instant.now());
            Instant end = calcWeeklySeasonEnd(start);
            setSeasonManual(1, start, end);
            log.info("서버 시작: 시즌 정보 없음 → 시즌 1 자동 생성 ({}~{})", start, end);
            return;
        }

        PvpSeason season = currentOpt.get();

        // Redis 재시작으로 시즌 정보 유실 시 복구
        redisService.setPvpSeasonInfo(
                season.getSeasonNumber(),
                season.getStartTime().toString(),
                season.getEndTime().toString()
        );

        if (season.isRewardDistributed() == false && Instant.now().isAfter(season.getEndTime())) {
            log.info("서버 시작: 시즌 {} 종료 미처리 감지 → 즉시 종료 처리", season.getSeasonNumber());
            endSeasonAndStartNext(season);
            return;
        }

        log.info("서버 시작: 시즌 {} 이어서 진행 (종료={}, 보상지급={})",
                season.getSeasonNumber(), season.getEndTime(), season.isRewardDistributed());
    }

    // ── 주간 시즌 날짜 계산 헬퍼 ──────────────────────────────────────────
    // 기준 시각이 속한 주의 월요일 00:00 UTC
    private Instant calcWeeklySeasonStart(Instant reference) {
        LocalDate refDate = reference.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate monday = refDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // 시즌 시작(월요일) + 7일 = 다음 주 월요일 00:00 UTC
    private Instant calcWeeklySeasonEnd(Instant seasonStart) {
        return seasonStart.plus(7, ChronoUnit.DAYS);
    }

    // ── 1시간 주기 자동 시즌 종료 체크 ────────────────────────────────────
    @Scheduled(fixedRate = 3_600_000)
    public void autoCheckSeasonEnd() {
        Optional<PvpSeason> currentOpt = getCurrentSeason();
        if (currentOpt.isPresent() == false) return;

        PvpSeason season = currentOpt.get();
        if (season.isRewardDistributed()) return;
        if (Instant.now().isBefore(season.getEndTime())) return;

        log.info("시즌 {} 자동 종료 감지, 처리 시작", season.getSeasonNumber());
        endSeasonAndStartNext(season);
    }
}




