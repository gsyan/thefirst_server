package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.Fleet;
import com.bk.sbs.entity.PvpRecord;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.PvpRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class PvpService {

    @Value("${ranking.pvp.sync.rate-minutes:60}")
    private long pvpSyncRateMinutes;

    private final RedisService redisService;
    private final PvpRecordRepository pvpRecordRepository;
    private final FleetService fleetService;
    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;

    public PvpService(RedisService redisService, PvpRecordRepository pvpRecordRepository,
                      FleetService fleetService, CommanderRepository commanderRepository,
                      GameDataService gameDataService) {
        this.redisService = redisService;
        this.pvpRecordRepository = pvpRecordRepository;
        this.fleetService = fleetService;
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
    }

    // 서버 시작 시 Redis를 DB 상태로 동기화 (고아 키 제거) - TestDataInitializer(@Order(1)) 이후 실행
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void syncRedisFromDb() {
        redisService.clearAllPvpData();

        List<PvpRecord> records = pvpRecordRepository.findAll();
        if (records.isEmpty()) {
            log.info("PVP Redis 동기화: DB 레코드 없음, Redis 클리어만 수행");
            return;
        }

        // commanderId → commanderName 일괄 로드
        List<Long> ids = records.stream()
                .map(PvpRecord::getCommanderId)
                .collect(java.util.stream.Collectors.toList());
        Map<Long, String> nameMap = new HashMap<>();
        commanderRepository.findAllById(ids).forEach(c -> nameMap.put(c.getId(), c.getCommanderName()));

        DataTableConfig config = gameDataService.getDataTableConfig();
        for (PvpRecord record : records) {
            redisService.setPvpScore(record.getCommanderId(), record.getScore());
            redisService.initPvpInfo(record.getCommanderId(), config.getPvpListRefreshCount(), record.getScore());
            String name = nameMap.get(record.getCommanderId());
            if (name != null) redisService.setRankName(record.getCommanderId(), name);

            FleetInfoDto fleet = fleetService.getActiveFleet(record.getCommanderId());
            String statJson = fleetService.computeFleetRankStatJson(fleet);
            if (statJson != null) redisService.setRankStat(record.getCommanderId(), statJson);
        }

        redisService.snapshotPvpRanking();
        String nextUpdatedAt = java.time.Instant.now().plusSeconds(pvpSyncRateMinutes * 60).toString();
        redisService.setPvpRankingUpdatedAt(nextUpdatedAt);
        log.info("PVP Redis 동기화 완료: {}건", records.size());
    }

    // PVP 랭킹 주기 재동기화 - DB → pvp:ranking 재구축 후 snapshot + stat 갱신
    @org.springframework.scheduling.annotation.Scheduled(fixedRateString = "#{${ranking.pvp.sync.rate-minutes:60} * 60000}")
    public void syncPvpRankingFromDb() {
        redisService.clearPvpRankingZset();

        List<PvpRecord> records = pvpRecordRepository.findAll();
        for (PvpRecord record : records) {
            redisService.setPvpScore(record.getCommanderId(), record.getScore());

            FleetInfoDto fleet = fleetService.getActiveFleet(record.getCommanderId());
            String statJson = fleetService.computeFleetRankStatJson(fleet);
            if (statJson != null) redisService.setRankStat(record.getCommanderId(), statJson);
        }

        redisService.snapshotPvpRanking();
        String nextUpdatedAt = java.time.Instant.now().plusSeconds(pvpSyncRateMinutes * 60).toString();
        redisService.setPvpRankingUpdatedAt(nextUpdatedAt);
        log.info("PVP 랭킹 주기 동기화 완료: {}건", records.size());
    }

    // PvP 최초 접근 시 Lazy 초기화 (Redis + DB)
    // @Transactional 제거 - initTestData() 미커밋 트랜잭션과 충돌 시 DataIntegrityViolationException 핸들링을 위해
    public PvpRecord getOrCreatePvpRecord(Long commanderId) {
        Optional<PvpRecord> existing = pvpRecordRepository.findByCommanderId(commanderId);
        if (existing.isPresent()) {
            Double redisScore = redisService.getPvpScore(commanderId);
            if (redisScore == null) {
                PvpRecord record = existing.get();
                redisService.setPvpScore(commanderId, record.getScore());
                DataTableConfig config = gameDataService.getDataTableConfig();
                redisService.initPvpInfo(commanderId, config.getPvpListRefreshCount(), record.getScore());
            }
            return existing.get();
        }

        DataTableConfig config = gameDataService.getDataTableConfig();
        int initScore = config.getPvpRankScoreInit();

        PvpRecord record = new PvpRecord();
        record.setCommanderId(commanderId);
        record.setScore(initScore);
        record.setWins(0);
        record.setLosses(0);
        record.setLastUpdated(Instant.now());

        try {
            pvpRecordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // initTestData() 미커밋 트랜잭션 또는 동시 요청으로 이미 INSERT된 경우
            PvpRecord created = pvpRecordRepository.findByCommanderId(commanderId)
                    .orElseThrow(() -> new IllegalStateException("pvp_record not found after constraint violation", e));
            if (redisService.getPvpScore(commanderId) == null) {
                redisService.setPvpScore(commanderId, created.getScore());
                redisService.initPvpInfo(commanderId, config.getPvpListRefreshCount(), created.getScore());
            }
            return created;
        }

        redisService.setPvpScore(commanderId, initScore);
        redisService.initPvpInfo(commanderId, config.getPvpListRefreshCount(), initScore);

        // 신규 캐릭터 이름도 rankName에 등록
        commanderRepository.findById(commanderId)
                .ifPresent(c -> redisService.setRankName(commanderId, c.getCommanderName()));

        return record;
    }

    // 대전 상대 리스트 조회
    public PvpListResponse getOpponentList(Long commanderId) {
        getOrCreatePvpRecord(commanderId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        int listCount = config.getPvpListCount();

        List<Long> cachedIds = redisService.getCachedOpponentList(commanderId);
        if (cachedIds != null && cachedIds.size() >= listCount) {
            return buildPvpListResponse(cachedIds);
        }

        List<Long> opponentIds = findOpponents(commanderId, listCount);
        redisService.cacheOpponentList(commanderId, opponentIds);

        return buildPvpListResponse(opponentIds);
    }

    // 상대 리스트 새로고침
    public PvpRefreshResponse refreshOpponentList(Long commanderId) {
        getOrCreatePvpRecord(commanderId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        int refreshRemain = redisService.getRefreshRemain(commanderId, config.getPvpListRefreshCount());
        if (refreshRemain <= 0) {
            throw new BusinessException(ServerErrorCode.PVP_REFRESH_LIMIT_EXCEEDED);
        }

        redisService.decrementRefreshRemain(commanderId);
        redisService.deleteCachedOpponentList(commanderId);

        List<Long> opponentIds = findOpponents(commanderId, config.getPvpListCount());
        redisService.cacheOpponentList(commanderId, opponentIds);

        List<PvpOpponentInfoDto> opponents = buildOpponentInfoList(opponentIds);

        PvpRefreshResponse response = new PvpRefreshResponse();
        response.setOpponents(opponents);
        response.setRefreshRemain(refreshRemain - 1);
        return response;
    }

    // 전투 시작
    public PvpBattleStartResponse startBattle(Long commanderId, Long opponentCommanderId) {
        getOrCreatePvpRecord(commanderId);

        Integer minCommanderLevel = gameDataService.getDataTableConfig().getPvpMinCommanderLevel();
        if (minCommanderLevel != null && minCommanderLevel > 0) {
            int myCommanderLevel = commanderRepository.findById(commanderId)
                    .map(com.bk.sbs.entity.Commander::getCommanderLevel)
                    .orElse(0);
            if (myCommanderLevel < minCommanderLevel) {
                throw new BusinessException(ServerErrorCode.PVP_COMMANDER_LEVEL_TOO_LOW);
            }
        }

        FleetInfoDto opponentFleet = fleetService.getActiveFleet(opponentCommanderId);
        if (opponentFleet == null) {
            throw new BusinessException(ServerErrorCode.PVP_OPPONENT_FLEET_NOT_FOUND);
        }

        // PVP 상대 함대는 미네랄 투입 레벨업 제외, 순수 모듈포인트 강화분만 전달
        FleetInfoDto strippedFleet = fleetService.stripMineralLevels(opponentFleet);

        String battleToken = UUID.randomUUID().toString();
        redisService.saveBattleToken(battleToken, commanderId, opponentCommanderId);

        PvpBattleStartResponse response = new PvpBattleStartResponse();
        response.setOpponentFleetInfo(strippedFleet);
        response.setBattleToken(battleToken);
        return response;
    }

    // 전투 결과 처리
    @Transactional
    public PvpBattleResultResponse reportBattleResult(Long commanderId, String battleToken, boolean isVictory) {
        Map<String, Long> tokenData = redisService.getBattleToken(battleToken);
        if (tokenData == null) {
            throw new BusinessException(ServerErrorCode.PVP_BATTLE_TOKEN_INVALID);
        }

        Long attackerId = ((Number) tokenData.get("attackerId")).longValue();
        Long defenderId = ((Number) tokenData.get("defenderId")).longValue();
        if (attackerId.equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.PVP_BATTLE_TOKEN_INVALID);
        }

        redisService.deleteBattleToken(battleToken);

        Double attackerScoreD = redisService.getPvpScore(attackerId);
        Double defenderScoreD = redisService.getPvpScore(defenderId);
        int attackerScore = attackerScoreD != null ? attackerScoreD.intValue() : 1000;
        int defenderScore = defenderScoreD != null ? defenderScoreD.intValue() : 1000;

        DataTableConfig config = gameDataService.getDataTableConfig();
        int penalty = config.getPvpRankScorePenalty();
        int winnerId, loserId;
        int winnerScore, loserScore;

        if (isVictory) {
            winnerId = attackerId.intValue();
            loserId = defenderId.intValue();
            winnerScore = attackerScore;
            loserScore = defenderScore;
        } else {
            winnerId = defenderId.intValue();
            loserId = attackerId.intValue();
            winnerScore = defenderScore;
            loserScore = attackerScore;
        }

        int[] changes = calculateScoreChange(winnerScore, loserScore, penalty);
        int winnerChange = changes[0];
        int loserChange = changes[1];

        // pvp:ranking ZSET 실시간 반영 (매칭용) / snapshot은 주기 동기화 시에만 갱신
        int attackerChange = isVictory ? winnerChange : loserChange;
        int defenderChange = isVictory ? loserChange : winnerChange;
        redisService.incrementPvpScore(attackerId, attackerChange);
        redisService.incrementPvpScore(defenderId, defenderChange);

        if (isVictory) {
            redisService.incrementWins(attackerId);
            redisService.incrementLosses(defenderId);
        } else {
            redisService.incrementLosses(attackerId);
            redisService.incrementWins(defenderId);
        }

        updatePvpRecordDb(attackerId, attackerChange, isVictory);
        updatePvpRecordDb(defenderId, defenderChange, isVictory == false);

        Double newScoreD = redisService.getPvpScore(attackerId);
        int newScoreVal = newScoreD != null ? newScoreD.intValue() : 0;
        Long newRank = redisService.getPvpSnapshotRank(attackerId);

        PvpBattleResultResponse response = new PvpBattleResultResponse();
        response.setScoreChange(attackerChange);
        response.setNewScore(newScoreVal);
        response.setNewRank(newRank != null ? newRank.intValue() : 0);
        return response;
    }

    // 점수 변동 계산
    public int[] calculateScoreChange(int winnerScore, int loserScore, int penalty) {
        int diff = Math.abs(winnerScore - loserScore);
        int maxBracket = 10 / penalty;
        int bracket = Math.min(diff / 100, maxBracket);

        if (bracket == 0) {
            return new int[]{10, -10};
        }

        boolean winnerIsHigher = winnerScore > loserScore;
        int winnerChange, loserChange;

        if (winnerIsHigher) {
            winnerChange = maxBracket + 1 - bracket;
            loserChange = -(10 * penalty * bracket);
        } else {
            winnerChange = 10 * penalty * bracket;
            loserChange = -(maxBracket + 1 - bracket);
        }

        return new int[]{winnerChange, loserChange};
    }

    // 매칭: 점수 범위 확장 검색
    private List<Long> findOpponents(Long commanderId, int count) {
        Double myScore = redisService.getPvpScore(commanderId);
        if (myScore == null) return Collections.emptyList();

        List<Long> result = new ArrayList<>();
        int maxExpand = 10;

        for (int i = 1; i <= maxExpand && result.size() < count; i++) {
            double range = i * 100.0;
            Set<String> candidates = redisService.findPvpByScoreRange(
                    myScore - range, myScore + range, count * 3L);

            for (String candidateId : candidates) {
                Long cId = Long.parseLong(candidateId);
                if (cId.equals(commanderId) == false && result.contains(cId) == false) {
                    result.add(cId);
                    if (result.size() >= count) break;
                }
            }
        }

        return result;
    }

    // 내 랭크 정보 조회 - score는 pvp:ranking 실시간, rank는 snapshot 기준
    public PvpMyRankResponse getMyRank(Long commanderId) {
        getOrCreatePvpRecord(commanderId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        Double myScoreD = redisService.getPvpScore(commanderId);
        int myScore = myScoreD != null ? myScoreD.intValue() : 0;
        Long myRank = redisService.getPvpSnapshotRank(commanderId);
        Map<Object, Object> myInfo = redisService.getPvpInfo(commanderId);
        int refreshRemain = redisService.getRefreshRemain(commanderId, config.getPvpListRefreshCount());

        PvpRankInfoDto rankInfo = new PvpRankInfoDto();
        rankInfo.setPvpScore(myScore > 0 ? myScore : config.getPvpRankScoreInit());
        rankInfo.setPvpRank(myRank != null ? myRank.intValue() : 0);
        rankInfo.setPvpWins(getIntFromHash(myInfo, "wins"));
        rankInfo.setPvpLosses(getIntFromHash(myInfo, "losses"));
        rankInfo.setPvpListRefreshRemain(refreshRemain);
        rankInfo.setSeasonNumber(redisService.getPvpSeasonNumber());
        rankInfo.setSeasonEndTime(redisService.getPvpSeasonEnd());

        PvpMyRankResponse response = new PvpMyRankResponse();
        response.setMyRankInfo(rankInfo);
        return response;
    }

    private PvpListResponse buildPvpListResponse(List<Long> opponentIds) {
        List<PvpOpponentInfoDto> opponents = buildOpponentInfoList(opponentIds);
        PvpListResponse response = new PvpListResponse();
        response.setOpponents(opponents);
        return response;
    }

    private List<PvpOpponentInfoDto> buildOpponentInfoList(List<Long> opponentIds) {
        List<PvpOpponentInfoDto> opponents = new ArrayList<>();
        for (Long opponentId : opponentIds) {
            Commander commander = commanderRepository.findById(opponentId).orElse(null);
            if (commander == null) continue;

            FleetInfoDto fleet = fleetService.getActiveFleet(opponentId);
            FleetInfoDto strippedFleet = fleetService.stripMineralLevels(fleet);

            Double score = redisService.getPvpScore(opponentId);
            Long rank = redisService.getPvpRank(opponentId);

            PvpOpponentInfoDto info = new PvpOpponentInfoDto();
            info.setCommanderId(opponentId);
            info.setCommanderName(commander.getCommanderName());
            info.setPvpScore(score != null ? score.intValue() : 1000);
            info.setRank(rank != null ? rank.intValue() : 0);
            info.setFleetInfo(strippedFleet);
            opponents.add(info);
        }
        return opponents;
    }

    private void updatePvpRecordDb(Long commanderId, int scoreChange, boolean isWin) {
        pvpRecordRepository.findByCommanderId((long) commanderId).ifPresent(record -> {
            record.setScore(record.getScore() + scoreChange);
            if (isWin) record.setWins(record.getWins() + 1);
            else record.setLosses(record.getLosses() + 1);
            record.setLastUpdated(Instant.now());
            pvpRecordRepository.save(record);
        });
    }

    private int getIntFromHash(Map<Object, Object> hash, String key) {
        if (hash == null) return 0;
        Object val = hash.get(key);
        if (val == null) return 0;
        return Integer.parseInt(val.toString());
    }
}








