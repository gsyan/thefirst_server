package com.bk.sbs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

// Redis를 이용한 PvP 랭킹/매칭/전투토큰 관리
@Service
@Slf4j
public class PvpRedisService {

    private static final String RANKING_KEY = "pvp:ranking";
    private static final String INFO_PREFIX = "pvp:info:";
    private static final String BATTLE_PREFIX = "pvp:battle:";
    private static final String LIST_PREFIX = "pvp:list:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PvpRedisService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ZSET 점수 등록/갱신
    public void setScore(Long characterId, int score) {
        redisTemplate.opsForZSet().add(RANKING_KEY, characterId.toString(), score);
    }

    // ZSET 점수 증감
    public Double incrementScore(Long characterId, int delta) {
        return redisTemplate.opsForZSet().incrementScore(RANKING_KEY, characterId.toString(), delta);
    }

    // 내 점수 조회 (없으면 null)
    public Double getScore(Long characterId) {
        return redisTemplate.opsForZSet().score(RANKING_KEY, characterId.toString());
    }

    // 내 순위 조회 (높은점수=1위, 0-indexed -> +1)
    public Long getRank(Long characterId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(RANKING_KEY, characterId.toString());
        return rank != null ? rank + 1 : null;
    }

    // 점수 범위로 상대 검색 (매칭)
    public Set<String> findByScoreRange(double minScore, double maxScore, long count) {
        Set<ZSetOperations.TypedTuple<String>> results = redisTemplate.opsForZSet()
                .rangeByScoreWithScores(RANKING_KEY, minScore, maxScore);
        if (results == null) return Collections.emptySet();

        return results.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .limit(count)
                .collect(Collectors.toSet());
    }

    // HASH 승리 수 증가
    public void incrementWins(Long characterId) {
        redisTemplate.opsForHash().increment(INFO_PREFIX + characterId, "wins", 1);
    }

    // HASH 패배 수 증가
    public void incrementLosses(Long characterId) {
        redisTemplate.opsForHash().increment(INFO_PREFIX + characterId, "losses", 1);
    }

    // HASH 전적 조회
    public Map<Object, Object> getInfo(Long characterId) {
        return redisTemplate.opsForHash().entries(INFO_PREFIX + characterId);
    }

    // HASH 초기화
    public void initInfo(Long characterId, int refreshCount) {
        String key = INFO_PREFIX + characterId;
        redisTemplate.opsForHash().put(key, "wins", "0");
        redisTemplate.opsForHash().put(key, "losses", "0");
        redisTemplate.opsForHash().put(key, "refreshRemain", String.valueOf(refreshCount));
        redisTemplate.opsForHash().put(key, "lastRefreshDate", LocalDate.now().toString());
    }

    // 새로고침 남은 횟수 조회 (날짜 변경 시 자동 리셋)
    public int getRefreshRemain(Long characterId, int maxRefresh) {
        String key = INFO_PREFIX + characterId;
        String lastDate = (String) redisTemplate.opsForHash().get(key, "lastRefreshDate");
        String today = LocalDate.now().toString();

        if (lastDate == null || lastDate.equals(today) == false) {
            redisTemplate.opsForHash().put(key, "refreshRemain", String.valueOf(maxRefresh));
            redisTemplate.opsForHash().put(key, "lastRefreshDate", today);
            return maxRefresh;
        }

        String remain = (String) redisTemplate.opsForHash().get(key, "refreshRemain");
        return remain != null ? Integer.parseInt(remain) : maxRefresh;
    }

    // 새로고침 횟수 1 감소
    public void decrementRefreshRemain(Long characterId) {
        redisTemplate.opsForHash().increment(INFO_PREFIX + characterId, "refreshRemain", -1);
    }

    // 전투 토큰 저장 (TTL 10분)
    public void saveBattleToken(String token, Long attackerId, Long defenderId) {
        try {
            Map<String, Long> data = Map.of("attackerId", attackerId, "defenderId", defenderId);
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(BATTLE_PREFIX + token, json, Duration.ofMinutes(10));
        } catch (JsonProcessingException e) {
            log.error("전투 토큰 저장 실패", e);
        }
    }

    // 전투 토큰 조회
    @SuppressWarnings("unchecked")
    public Map<String, Long> getBattleToken(String token) {
        String json = redisTemplate.opsForValue().get(BATTLE_PREFIX + token);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("전투 토큰 파싱 실패", e);
            return null;
        }
    }

    // 전투 토큰 삭제
    public void deleteBattleToken(String token) {
        redisTemplate.delete(BATTLE_PREFIX + token);
    }

    // 매칭 리스트 캐싱 (TTL 24시간)
    public void cacheOpponentList(Long characterId, List<Long> opponentIds) {
        String key = LIST_PREFIX + characterId;
        redisTemplate.delete(key);
        for (Long id : opponentIds) {
            redisTemplate.opsForList().rightPush(key, id.toString());
        }
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    // 캐시된 매칭 리스트 조회
    public List<Long> getCachedOpponentList(Long characterId) {
        String key = LIST_PREFIX + characterId;
        List<String> list = redisTemplate.opsForList().range(key, 0, -1);
        if (list == null || list.isEmpty()) return null;
        return list.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    // 캐시된 매칭 리스트 삭제
    public void deleteCachedOpponentList(Long characterId) {
        redisTemplate.delete(LIST_PREFIX + characterId);
    }
}
