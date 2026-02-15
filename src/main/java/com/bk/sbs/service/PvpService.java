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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class PvpService {

    private final PvpRedisService pvpRedisService;
    private final PvpRecordRepository pvpRecordRepository;
    private final FleetService fleetService;
    private final CharacterRepository characterRepository;
    private final GameDataService gameDataService;

    public PvpService(PvpRedisService pvpRedisService, PvpRecordRepository pvpRecordRepository,
                      FleetService fleetService, CharacterRepository characterRepository,
                      GameDataService gameDataService) {
        this.pvpRedisService = pvpRedisService;
        this.pvpRecordRepository = pvpRecordRepository;
        this.fleetService = fleetService;
        this.characterRepository = characterRepository;
        this.gameDataService = gameDataService;
    }

    // PvP 최초 접근 시 Lazy 초기화 (Redis + DB)
    @Transactional
    public PvpRecord getOrCreatePvpRecord(Long characterId) {
        Optional<PvpRecord> existing = pvpRecordRepository.findByCharacterId(characterId);
        if (existing.isPresent()) {
            // Redis에도 확인
            Double redisScore = pvpRedisService.getScore(characterId);
            if (redisScore == null) {
                // Redis에 없으면 DB에서 복원
                PvpRecord record = existing.get();
                pvpRedisService.setScore(characterId, record.getScore());
                DataTableConfig config = gameDataService.getDataTableConfig();
                pvpRedisService.initInfo(characterId, config.getPvpListRefreshCount());
            }
            return existing.get();
        }

        // 신규 생성
        DataTableConfig config = gameDataService.getDataTableConfig();
        int initScore = config.getPvpRankScoreInit();

        PvpRecord record = new PvpRecord();
        record.setCharacterId(characterId);
        record.setScore(initScore);
        record.setWins(0);
        record.setLosses(0);
        record.setLastUpdated(LocalDateTime.now());
        pvpRecordRepository.save(record);

        // Redis 초기화
        pvpRedisService.setScore(characterId, initScore);
        pvpRedisService.initInfo(characterId, config.getPvpListRefreshCount());

        return record;
    }

    // 대전 상대 리스트 조회
    public PvpListResponse getOpponentList(Long characterId) {
        getOrCreatePvpRecord(characterId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        int listCount = config.getPvpListCount();

        // 캐시된 리스트 확인
        List<Long> cachedIds = pvpRedisService.getCachedOpponentList(characterId);
        if (cachedIds != null && cachedIds.size() >= listCount) {
            return buildPvpListResponse(characterId, cachedIds, config);
        }

        // 매칭 수행
        List<Long> opponentIds = findOpponents(characterId, listCount);
        pvpRedisService.cacheOpponentList(characterId, opponentIds);

        return buildPvpListResponse(characterId, opponentIds, config);
    }

    // 상대 리스트 새로고침
    public PvpRefreshResponse refreshOpponentList(Long characterId) {
        getOrCreatePvpRecord(characterId);

        DataTableConfig config = gameDataService.getDataTableConfig();
        int refreshRemain = pvpRedisService.getRefreshRemain(characterId, config.getPvpListRefreshCount());
        if (refreshRemain <= 0) {
            throw new BusinessException(ServerErrorCode.PVP_REFRESH_LIMIT_EXCEEDED);
        }

        pvpRedisService.decrementRefreshRemain(characterId);
        pvpRedisService.deleteCachedOpponentList(characterId);

        List<Long> opponentIds = findOpponents(characterId, config.getPvpListCount());
        pvpRedisService.cacheOpponentList(characterId, opponentIds);

        List<PvpOpponentInfoDto> opponents = buildOpponentInfoList(opponentIds);

        PvpRefreshResponse response = new PvpRefreshResponse();
        response.setOpponents(opponents);
        response.setRefreshRemain(refreshRemain - 1);
        return response;
    }

    // 전투 시작
    public PvpBattleStartResponse startBattle(Long characterId, Long opponentCharacterId) {
        getOrCreatePvpRecord(characterId);

        // 상대 활성 함대 조회
        FleetInfoDto opponentFleet = fleetService.getActiveFleet(opponentCharacterId);
        if (opponentFleet == null) {
            throw new BusinessException(ServerErrorCode.PVP_OPPONENT_FLEET_NOT_FOUND);
        }

        // battleToken 생성
        String battleToken = UUID.randomUUID().toString();
        pvpRedisService.saveBattleToken(battleToken, characterId, opponentCharacterId);

        PvpBattleStartResponse response = new PvpBattleStartResponse();
        response.setOpponentFleetInfo(opponentFleet);
        response.setBattleToken(battleToken);
        return response;
    }

    // 전투 결과 처리
    @Transactional
    public PvpBattleResultResponse reportBattleResult(Long characterId, String battleToken, boolean isVictory) {
        // 토큰 유효성 검증
        Map<String, Long> tokenData = pvpRedisService.getBattleToken(battleToken);
        if (tokenData == null) {
            throw new BusinessException(ServerErrorCode.PVP_BATTLE_TOKEN_INVALID);
        }

        Long attackerId = ((Number) tokenData.get("attackerId")).longValue();
        Long defenderId = ((Number) tokenData.get("defenderId")).longValue();
        if (attackerId.equals(characterId) == false) {
            throw new BusinessException(ServerErrorCode.PVP_BATTLE_TOKEN_INVALID);
        }

        pvpRedisService.deleteBattleToken(battleToken);

        // 점수 조회
        Double attackerScoreD = pvpRedisService.getScore(attackerId);
        Double defenderScoreD = pvpRedisService.getScore(defenderId);
        int attackerScore = attackerScoreD != null ? attackerScoreD.intValue() : 1000;
        int defenderScore = defenderScoreD != null ? defenderScoreD.intValue() : 1000;

        // 점수 변동 계산
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

        // Redis 점수 갱신
        pvpRedisService.incrementScore(attackerId, isVictory ? winnerChange : loserChange);
        pvpRedisService.incrementScore(defenderId, isVictory ? loserChange : winnerChange);

        // Redis 전적 갱신
        if (isVictory) {
            pvpRedisService.incrementWins(attackerId);
            pvpRedisService.incrementLosses(defenderId);
        } else {
            pvpRedisService.incrementLosses(attackerId);
            pvpRedisService.incrementWins(defenderId);
        }

        // DB 백업 (attacker)
        int attackerChange = isVictory ? winnerChange : loserChange;
        updatePvpRecordDb(attackerId, attackerChange, isVictory);
        // DB 백업 (defender)
        int defenderChange = isVictory ? loserChange : winnerChange;
        updatePvpRecordDb(defenderId, defenderChange, isVictory == false);

        // 응답
        Double newScore = pvpRedisService.getScore(attackerId);
        Long newRank = pvpRedisService.getRank(attackerId);

        PvpBattleResultResponse response = new PvpBattleResultResponse();
        response.setScoreChange(attackerChange);
        response.setNewScore(newScore != null ? newScore.intValue() : attackerScore + attackerChange);
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
        Double myScore = pvpRedisService.getScore(characterId);
        if (myScore == null) return Collections.emptyList();

        List<Long> result = new ArrayList<>();
        int maxExpand = 10;

        for (int i = 1; i <= maxExpand && result.size() < count; i++) {
            double range = i * 100.0;
            Set<String> candidates = pvpRedisService.findByScoreRange(
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

    // PvpListResponse 빌드
    private PvpListResponse buildPvpListResponse(Long characterId, List<Long> opponentIds, DataTableConfig config) {
        List<PvpOpponentInfoDto> opponents = buildOpponentInfoList(opponentIds);

        Double myScore = pvpRedisService.getScore(characterId);
        Long myRank = pvpRedisService.getRank(characterId);
        Map<Object, Object> myInfo = pvpRedisService.getInfo(characterId);
        int refreshRemain = pvpRedisService.getRefreshRemain(characterId, config.getPvpListRefreshCount());

        PvpRankInfoDto rankInfo = new PvpRankInfoDto();
        rankInfo.setPvpScore(myScore != null ? myScore.intValue() : config.getPvpRankScoreInit());
        rankInfo.setPvpRank(myRank != null ? myRank.intValue() : 0);
        rankInfo.setPvpWins(getIntFromHash(myInfo, "wins"));
        rankInfo.setPvpLosses(getIntFromHash(myInfo, "losses"));
        rankInfo.setPvpListRefreshRemain(refreshRemain);

        PvpListResponse response = new PvpListResponse();
        response.setOpponents(opponents);
        response.setMyRankInfo(rankInfo);
        return response;
    }

    // 상대 정보 리스트 빌드
    private List<PvpOpponentInfoDto> buildOpponentInfoList(List<Long> opponentIds) {
        List<PvpOpponentInfoDto> opponents = new ArrayList<>();
        for (Long opponentId : opponentIds) {
            Character character = characterRepository.findById(opponentId).orElse(null);
            if (character == null) continue;

            FleetInfoDto fleet = fleetService.getActiveFleet(opponentId);

            Double score = pvpRedisService.getScore(opponentId);
            Long rank = pvpRedisService.getRank(opponentId);

            PvpOpponentInfoDto info = new PvpOpponentInfoDto();
            info.setCharacterId(opponentId);
            info.setCharacterName(character.getCharacterName());
            info.setPvpScore(score != null ? score.intValue() : 1000);
            info.setRank(rank != null ? rank.intValue() + 1 : 0);
            info.setFleetInfo(fleet);
            opponents.add(info);
        }
        return opponents;
    }

    // DB 백업 업데이트
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
