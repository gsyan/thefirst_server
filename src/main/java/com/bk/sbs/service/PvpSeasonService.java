package com.bk.sbs.service;

import com.bk.sbs.config.DataTablePvpSeason;
import com.bk.sbs.entity.PvpRecord;
import com.bk.sbs.entity.PvpSeason;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.PvpRecordRepository;
import com.bk.sbs.repository.PvpSeasonRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PvpSeasonService {

    private final PvpSeasonRepository pvpSeasonRepository;
    private final PvpRecordRepository pvpRecordRepository;
    private final CharacterRepository characterRepository;
    private final RedisService redisService;
    private final GameDataService gameDataService;

    public PvpSeasonService(PvpSeasonRepository pvpSeasonRepository,
                            PvpRecordRepository pvpRecordRepository,
                            CharacterRepository characterRepository,
                            RedisService redisService,
                            GameDataService gameDataService) {
        this.pvpSeasonRepository = pvpSeasonRepository;
        this.pvpRecordRepository = pvpRecordRepository;
        this.characterRepository = characterRepository;
        this.redisService = redisService;
        this.gameDataService = gameDataService;
    }

    // ── 조회 ───────────────────────────────────────────────────────────────

    public Optional<PvpSeason> getCurrentSeason() {
        return pvpSeasonRepository.findTopByOrderBySeasonNumberDesc();
    }

    // ── 수동 시즌 설정 (진행 중에도 변경 가능) ─────────────────────────────

    @Transactional
    public PvpSeason setSeasonManual(int seasonNumber, String seasonName, Instant startTime, Instant endTime) {
        PvpSeason season = pvpSeasonRepository.findById(seasonNumber)
                .orElse(new PvpSeason());

        season.setSeasonNumber(seasonNumber);
        season.setSeasonName(seasonName);
        season.setStartTime(startTime);
        season.setEndTime(endTime);
        pvpSeasonRepository.save(season);

        // Redis 업데이트
        redisService.setPvpSeasonInfo(seasonNumber, seasonName, startTime.toString(), endTime.toString());

        // 이전 시즌(seasonNumber-1) 보상 만료일을 이 시즌 종료일로 일괄 업데이트
        int prevSeasonNumber = seasonNumber - 1;
        if (prevSeasonNumber >= 1) {
            int updated = pvpSeasonRepository.bulkUpdatePvpMineralExpiry(prevSeasonNumber, endTime);
            if (updated > 0) {
                log.info("시즌 {} 종료일 변경 → 시즌 {} 보상 만료일 {}건 업데이트", seasonNumber, prevSeasonNumber, updated);
            }
        }

        log.info("PVP 시즌 설정 완료: 시즌={} name={} {} ~ {}", seasonNumber, seasonName, startTime, endTime);
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
        String nextSeasonName = "시즌 " + nextSeasonNumber;
        Instant nextStart = season.getEndTime();
        int durationDays = gameDataService.getDataTablePvpSeason().getDefaultSeasonDurationDays();
        Instant nextEnd = nextStart.plus(durationDays, ChronoUnit.DAYS);

        setSeasonManual(nextSeasonNumber, nextSeasonName, nextStart, nextEnd);
        log.info("다음 시즌 자동 시작: {}", nextSeasonName);
    }

    // ── 보상 지급 ──────────────────────────────────────────────────────────

    @Transactional
    public void distributeSeasonReward(PvpSeason season) {
        DataTablePvpSeason pvpSeasonTable = gameDataService.getDataTablePvpSeason();

        // 다음 시즌 종료일 = 보상 만료일
        int nextSeasonNumber = season.getSeasonNumber() + 1;
        Optional<PvpSeason> nextSeasonOpt = pvpSeasonRepository.findById(nextSeasonNumber);
        Instant rewardExpiry = nextSeasonOpt.map(PvpSeason::getEndTime)
                .orElse(season.getEndTime().plus(
                        pvpSeasonTable.getDefaultSeasonDurationDays(), ChronoUnit.DAYS));

        List<PvpRecord> records = pvpRecordRepository.findAll();
        int count = 0;
        for (PvpRecord record : records) {
            int reward = pvpSeasonTable.getSeasonReward(record.getScore());
            if (reward <= 0) continue;

            characterRepository.findById(record.getCharacterId()).ifPresent(character -> {
                character.setPvpMineral(character.getPvpMineral() + reward);
                character.setPvpMineralMaxGot(character.getPvpMineralMaxGot() + reward);
                character.setPvpMineralExpiry(rewardExpiry);
                character.setPvpMineralSeasonRef(season.getSeasonNumber());
                characterRepository.save(character);
            });
            count++;
        }

        season.setRewardDistributed(true);
        pvpSeasonRepository.save(season);
        log.info("시즌 {} 보상 지급 완료: {}명, 만료일={}", season.getSeasonNumber(), count, rewardExpiry);
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
            redisService.setPvpScore(record.getCharacterId(), resetScore);
        }

        redisService.snapshotPvpRanking();
        log.info("시즌 점수 리셋 완료: {}건", records.size());
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
