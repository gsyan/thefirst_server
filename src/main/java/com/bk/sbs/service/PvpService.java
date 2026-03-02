package com.bk.sbs.service;

import com.bk.sbs.config.DataTableConfig;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.Fleet;
import com.bk.sbs.entity.PvpRecord;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.PvpRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class PvpService {

    @Value("${ranking.pvp.sync.rate-minutes:60}")
    private long pvpSyncRateMinutes;

    private final RedisService redisService;
    private final PvpRecordRepository pvpRecordRepository;
    private final FleetService fleetService;
    private final CharacterRepository characterRepository;
    private final GameDataService gameDataService;

    public PvpService(RedisService redisService, PvpRecordRepository pvpRecordRepository,
                      FleetService fleetService, CharacterRepository characterRepository,
                      GameDataService gameDataService) {
        this.redisService = redisService;
        this.pvpRecordRepository = pvpRecordRepository;
        this.fleetService = fleetService;
        this.characterRepository = characterRepository;
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

        // characterId → characterName 일괄 로드
        List<Long> ids = records.stream()
                .map(PvpRecord::getCharacterId)
                .collect(java.util.stream.Collectors.toList());
        Map<Long, String> nameMap = new HashMap<>();
        characterRepository.findAllById(ids).forEach(c -> nameMap.put(c.getId(), c.getCharacterName()));

        DataTableConfig config = gameDataService.getDataTableConfig();
        for (PvpRecord record : records) {
            redisService.setPvpScore(record.getCharacterId(), record.getScore());
            redisService.initPvpInfo(record.getCharacterId(), config.getPvpListRefreshCount(), record.getScore());
            String name = nameMap.get(record.getCharacterId());
            if (name != null) redisService.setRankName(record.getCharacterId(), name);
        }

        redisService.snapshotPvpRanking();
        String nextUpdatedAt = java.time.Instant.now().plusSeconds(pvpSyncRateMinutes * 60).toString();
        redisService.setPvpRankingUpdatedAt(nextUpdatedAt);
        log.info("PVP Redis 동기화 완료: {}건", records.size());
    }

    // PVP 랭킹 주기 재동기화 - DB → pvp:ranking 재구축 후 snapshot 갱신
    @org.springframework.scheduling.annotation.Scheduled(fixedRateString = "#{${ranking.pvp.sync.rate-minutes:60} * 60000}")
    public void syncPvpRankingFromDb() {
        redisService.clearPvpRankingZset();

        List<PvpRecord> records = pvpRecordRepository.findAll();
        for (PvpRecord record : records) {
            redisService.setPvpScore(record.getCharacterId(), record.getScore());
        }

        redisService.snapshotPvpRanking();
        String nextUpdatedAt = java.time.Instant.now().plusSeconds(pvpSyncRateMinutes * 60).toString();
        redisService.setPvpRankingUpdatedAt(nextUpdatedAt);
        log.info("PVP 랭킹 주기 동기화 완료: {}건", records.size());
    }

    // PvP 최초 접근 시 Lazy 초기화 (Redis + DB)
    @Transactional
    public PvpRecord getOrCreatePvpRecord(Long characterId) {
        Optional<PvpRecord> existing = pvpRecordRepository.findByCharacterId(characterId);
        if (existing.isPresent()) {
            Double redisScore = redisService.getPvpScore(characterId);
            if (redisScore == null) {
                PvpRecord record = existing.get();
                redisService.setPvpScore(characterId, record.getScore());
                DataTableConfig config = gameDataService.getDataTableConfig();
                redisService.initPvpInfo(characterId, config.getPvpListRefreshCount(), record.getScore());
            }
            return existing.get();
        }

        DataTableConfig config = gameDataService.getDataTableConfig();
        int initScore = config.getPvpRankScoreInit();

        PvpRecord record = new PvpRecord();
        record.setCharacterId(characterId);
        record.setScore(initScore);
        record.setWins(0);
        record.setLosses(0);
        record.setLastUpdated(LocalDateTime.now());
        pvpRecordRepository.save(record);

        redisService.setPvpScore(characterId, initScore);
        redisService.initPvpInfo(characterId, config.getPvpListRefreshCount(), initScore);

        // 신규 캐릭터 이름도 rankName에 등록
        characterRepository.findById(characterId)
                .ifPresent(c -> redisService.setRankName(characterId, c.getCharacterName()));

        return record;
    }

    // 대전 상대 리스트 조회
    public PvpListResponse getOpponentList(Long characterId) {
        getOrCreatePvpRecord(characterId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        int listCount = config.getPvpListCount();

        List<Long> cachedIds = redisService.getCachedOpponentList(characterId);
        if (cachedIds != null && cachedIds.size() >= listCount) {
            return buildPvpListResponse(cachedIds);
        }

        List<Long> opponentIds = findOpponents(characterId, listCount);
        redisService.cacheOpponentList(characterId, opponentIds);

        return buildPvpListResponse(opponentIds);
    }

    // 상대 리스트 새로고침
    public PvpRefreshResponse refreshOpponentList(Long characterId) {
        getOrCreatePvpRecord(characterId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        int refreshRemain = redisService.getRefreshRemain(characterId, config.getPvpListRefreshCount());
        if (refreshRemain <= 0) {
            throw new BusinessException(ServerErrorCode.PVP_REFRESH_LIMIT_EXCEEDED);
        }

        redisService.decrementRefreshRemain(characterId);
        redisService.deleteCachedOpponentList(characterId);

        List<Long> opponentIds = findOpponents(characterId, config.getPvpListCount());
        redisService.cacheOpponentList(characterId, opponentIds);

        List<PvpOpponentInfoDto> opponents = buildOpponentInfoList(opponentIds);

        PvpRefreshResponse response = new PvpRefreshResponse();
        response.setOpponents(opponents);
        response.setRefreshRemain(refreshRemain - 1);
        return response;
    }

    // 전투 시작
    public PvpBattleStartResponse startBattle(Long characterId, Long opponentCharacterId) {
        getOrCreatePvpRecord(characterId);

        FleetInfoDto opponentFleet = fleetService.getActiveFleet(opponentCharacterId);
        if (opponentFleet == null) {
            throw new BusinessException(ServerErrorCode.PVP_OPPONENT_FLEET_NOT_FOUND);
        }

        String battleToken = UUID.randomUUID().toString();
        redisService.saveBattleToken(battleToken, characterId, opponentCharacterId);

        PvpBattleStartResponse response = new PvpBattleStartResponse();
        response.setOpponentFleetInfo(opponentFleet);
        response.setBattleToken(battleToken);
        return response;
    }

    // 전투 결과 처리
    @Transactional
    public PvpBattleResultResponse reportBattleResult(Long characterId, String battleToken, boolean isVictory) {
        Map<String, Long> tokenData = redisService.getBattleToken(battleToken);
        if (tokenData == null) {
            throw new BusinessException(ServerErrorCode.PVP_BATTLE_TOKEN_INVALID);
        }

        Long attackerId = ((Number) tokenData.get("attackerId")).longValue();
        Long defenderId = ((Number) tokenData.get("defenderId")).longValue();
        if (attackerId.equals(characterId) == false) {
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
    private List<Long> findOpponents(Long characterId, int count) {
        Double myScore = redisService.getPvpScore(characterId);
        if (myScore == null) return Collections.emptyList();

        List<Long> result = new ArrayList<>();
        int maxExpand = 10;

        for (int i = 1; i <= maxExpand && result.size() < count; i++) {
            double range = i * 100.0;
            Set<String> candidates = redisService.findPvpByScoreRange(
                    myScore - range, myScore + range, count * 3L);

            for (String candidateId : candidates) {
                Long cId = Long.parseLong(candidateId);
                if (cId.equals(characterId) == false && result.contains(cId) == false) {
                    result.add(cId);
                    if (result.size() >= count) break;
                }
            }
        }

        return result;
    }

    // 내 랭크 정보 조회 - score는 pvp:ranking 실시간, rank는 snapshot 기준
    public PvpMyRankResponse getMyRank(Long characterId) {
        getOrCreatePvpRecord(characterId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        Double myScoreD = redisService.getPvpScore(characterId);
        int myScore = myScoreD != null ? myScoreD.intValue() : 0;
        Long myRank = redisService.getPvpSnapshotRank(characterId);
        Map<Object, Object> myInfo = redisService.getPvpInfo(characterId);
        int refreshRemain = redisService.getRefreshRemain(characterId, config.getPvpListRefreshCount());

        PvpRankInfoDto rankInfo = new PvpRankInfoDto();
        rankInfo.setPvpScore(myScore > 0 ? myScore : config.getPvpRankScoreInit());
        rankInfo.setPvpRank(myRank != null ? myRank.intValue() : 0);
        rankInfo.setPvpWins(getIntFromHash(myInfo, "wins"));
        rankInfo.setPvpLosses(getIntFromHash(myInfo, "losses"));
        rankInfo.setPvpListRefreshRemain(refreshRemain);

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
            Character character = characterRepository.findById(opponentId).orElse(null);
            if (character == null) continue;

            FleetInfoDto fleet = fleetService.getActiveFleet(opponentId);

            Double score = redisService.getPvpScore(opponentId);
            Long rank = redisService.getPvpRank(opponentId);

            PvpOpponentInfoDto info = new PvpOpponentInfoDto();
            info.setCharacterId(opponentId);
            info.setCharacterName(character.getCharacterName());
            info.setPvpScore(score != null ? score.intValue() : 1000);
            info.setRank(rank != null ? rank.intValue() : 0);
            info.setFleetInfo(fleet);
            opponents.add(info);
        }
        return opponents;
    }

    private void updatePvpRecordDb(Long characterId, int scoreChange, boolean isWin) {
        pvpRecordRepository.findByCharacterId((long) characterId).ifPresent(record -> {
            record.setScore(record.getScore() + scoreChange);
            if (isWin) record.setWins(record.getWins() + 1);
            else record.setLosses(record.getLosses() + 1);
            record.setLastUpdated(LocalDateTime.now());
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
