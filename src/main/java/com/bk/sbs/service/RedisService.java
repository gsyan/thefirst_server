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

// PVP/Zone/Attack 랭킹, PVP 배틀 토큰/매칭 캐시 등 Redis 전역 관리
@Service
@Slf4j
public class RedisService {

    // ── 키 상수 ────────────────────────────────────────────────────────────
    private static final String PVP_RANKING_KEY          = "pvp:ranking";          // 실시간 매칭용
    private static final String PVP_RANKING_SNAPSHOT_KEY = "pvp:ranking:snapshot"; // 랭킹 보드 표시용 (주기 스냅샷)
    private static final String ZONE_RANKING_KEY          = "zone:ranking";          // 실시간 매칭용
    private static final String ZONE_RANKING_SNAPSHOT_KEY = "zone:ranking:snapshot"; // 랭킹 보드 표시용 (주기 스냅샷)
    private static final String PVP_INFO_PREFIX          = "pvp:info:";
    private static final String BATTLE_PREFIX            = "pvp:battle:";
    private static final String LIST_PREFIX              = "pvp:list:";
    private static final String RANK_NAME_KEY            = "rank:name";
    private static final String RANKING_UPDATED_PVP      = "ranking:pvp:updated";
    private static final String RANKING_UPDATED_ZONE     = "ranking:zone:updated";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════════════════
    // PVP 랭킹 ZSET
    // ══════════════════════════════════════════════════════════════════════

    public void setPvpScore(Long characterId, int score) {
        redisTemplate.opsForZSet().add(PVP_RANKING_KEY, characterId.toString(), score);
    }

    public Double incrementPvpScore(Long characterId, int delta) {
        return redisTemplate.opsForZSet().incrementScore(PVP_RANKING_KEY, characterId.toString(), delta);
    }

    public Double getPvpScore(Long characterId) {
        return redisTemplate.opsForZSet().score(PVP_RANKING_KEY, characterId.toString());
    }

    /** 높은 점수 = 1위, 0-indexed → +1 반환 */
    public Long getPvpRank(Long characterId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(PVP_RANKING_KEY, characterId.toString());
        return rank != null ? rank + 1 : null;
    }

    public Set<String> findPvpByScoreRange(double minScore, double maxScore, long count) {
        Set<ZSetOperations.TypedTuple<String>> results = redisTemplate.opsForZSet()
                .rangeByScoreWithScores(PVP_RANKING_KEY, minScore, maxScore);
        if (results == null) return Collections.emptySet();

        return results.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .limit(count)
                .collect(Collectors.toSet());
    }

    public long getTotalPvpCount() {
        Long count = redisTemplate.opsForZSet().zCard(PVP_RANKING_KEY);
        return count != null ? count : 0L;
    }

    public LinkedHashMap<Long, Integer> getPvpRankingPage(int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
                .reverseRangeWithScores(PVP_RANKING_KEY, offset, (long) offset + limit - 1);

        LinkedHashMap<Long, Integer> map = new LinkedHashMap<>();
        if (result == null) return map;

        for (ZSetOperations.TypedTuple<String> tuple : result) {
            if (tuple.getValue() == null) continue;
            int score = tuple.getScore() != null ? tuple.getScore().intValue() : 0;
            map.put(Long.parseLong(tuple.getValue()), score);
        }
        return map;
    }

    /** pvp:ranking ZSET만 삭제 (pvp:info/pvp:list 유지) */
    public void clearPvpRankingZset() {
        redisTemplate.delete(PVP_RANKING_KEY);
    }

    /** pvp:ranking → pvp:ranking:snapshot 복사 (랭킹 보드용 주기 스냅샷) */
    public void snapshotPvpRanking() {
        redisTemplate.delete(PVP_RANKING_SNAPSHOT_KEY);
        redisTemplate.opsForZSet().unionAndStore(PVP_RANKING_KEY, Collections.emptyList(), PVP_RANKING_SNAPSHOT_KEY);
    }

    public long getTotalPvpSnapshotCount() {
        Long count = redisTemplate.opsForZSet().zCard(PVP_RANKING_SNAPSHOT_KEY);
        return count != null ? count : 0L;
    }

    public LinkedHashMap<Long, Integer> getPvpSnapshotPage(int offset, int limit) {
        return buildLongScorePage(PVP_RANKING_SNAPSHOT_KEY, offset, limit);
    }

    public Long getPvpSnapshotRank(Long characterId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(PVP_RANKING_SNAPSHOT_KEY, characterId.toString());
        return rank != null ? rank + 1 : null;
    }

    /** pvp:info Hash에 개인 실시간 score 저장 */
    public void setPvpScorePersonal(Long characterId, int score) {
        redisTemplate.opsForHash().put(PVP_INFO_PREFIX + characterId, "score", String.valueOf(score));
    }

    public void incrementPvpScorePersonal(Long characterId, int delta) {
        redisTemplate.opsForHash().increment(PVP_INFO_PREFIX + characterId, "score", delta);
    }

    public int getPvpScorePersonal(Long characterId) {
        Object val = redisTemplate.opsForHash().get(PVP_INFO_PREFIX + characterId, "score");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Zone 랭킹 ZSET  (score = chapter * 1000 + stage)
    // ══════════════════════════════════════════════════════════════════════

    public void setZoneScore(Long characterId, long score) {
        redisTemplate.opsForZSet().add(ZONE_RANKING_KEY, characterId.toString(), score);
    }

    public long getTotalZoneCount() {
        Long count = redisTemplate.opsForZSet().zCard(ZONE_RANKING_KEY);
        return count != null ? count : 0L;
    }

    public LinkedHashMap<Long, Integer> getZoneRankingPage(int offset, int limit) {
        return buildLongScorePage(ZONE_RANKING_KEY, offset, limit);
    }

    /** 높은 점수 = 1위, 0-indexed → +1 반환 */
    public Long getZoneRank(Long characterId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(ZONE_RANKING_KEY, characterId.toString());
        return rank != null ? rank + 1 : null;
    }

    public Double getZoneScore(Long characterId) {
        return redisTemplate.opsForZSet().score(ZONE_RANKING_KEY, characterId.toString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 캐릭터 이름 Hash  (rankName → characterId → characterName)
    // ══════════════════════════════════════════════════════════════════════

    public void setRankName(Long characterId, String name) {
        redisTemplate.opsForHash().put(RANK_NAME_KEY, characterId.toString(), name != null ? name : "");
    }

    /** characterId 문자열 컬렉션 → {id → name} 맵 반환 (없으면 "Unknown") */
    public Map<String, String> getRankNamesMulti(Collection<String> characterIdStrs) {
        List<Object> keys = new ArrayList<>(characterIdStrs);
        List<Object> values = redisTemplate.opsForHash().multiGet(RANK_NAME_KEY, keys);
        Map<String, String> result = new HashMap<>();
        int i = 0;
        for (Object key : keys) {
            Object val = values.get(i++);
            result.put((String) key, (val != null && !val.toString().isEmpty()) ? val.toString() : "Unknown");
        }
        return result;
    }

    public void clearRankNameData() {
        redisTemplate.delete(RANK_NAME_KEY);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 랭킹 업데이트 시각  (ISO 8601 String)
    // ══════════════════════════════════════════════════════════════════════

    public void setPvpRankingUpdatedAt(String isoTime) {
        redisTemplate.opsForValue().set(RANKING_UPDATED_PVP, isoTime);
    }

    public String getPvpRankingUpdatedAt() {
        return redisTemplate.opsForValue().get(RANKING_UPDATED_PVP);
    }

    public void setZoneRankingUpdatedAt(String isoTime) {
        redisTemplate.opsForValue().set(RANKING_UPDATED_ZONE, isoTime);
    }

    public String getZoneRankingUpdatedAt() {
        return redisTemplate.opsForValue().get(RANKING_UPDATED_ZONE);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PVP INFO  (승/패/새로고침 횟수)
    // ══════════════════════════════════════════════════════════════════════

    public void incrementWins(Long characterId) {
        redisTemplate.opsForHash().increment(PVP_INFO_PREFIX + characterId, "wins", 1);
    }

    public void incrementLosses(Long characterId) {
        redisTemplate.opsForHash().increment(PVP_INFO_PREFIX + characterId, "losses", 1);
    }

    public Map<Object, Object> getPvpInfo(Long characterId) {
        return redisTemplate.opsForHash().entries(PVP_INFO_PREFIX + characterId);
    }

    public void initPvpInfo(Long characterId, int refreshCount, int score) {
        String key = PVP_INFO_PREFIX + characterId;
        redisTemplate.opsForHash().put(key, "wins", "0");
        redisTemplate.opsForHash().put(key, "losses", "0");
        redisTemplate.opsForHash().put(key, "refreshRemain", String.valueOf(refreshCount));
        redisTemplate.opsForHash().put(key, "lastRefreshDate", LocalDate.now().toString());
        redisTemplate.opsForHash().put(key, "score", String.valueOf(score));
    }

    public int getRefreshRemain(Long characterId, int maxRefresh) {
        String key = PVP_INFO_PREFIX + characterId;
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

    public void decrementRefreshRemain(Long characterId) {
        redisTemplate.opsForHash().increment(PVP_INFO_PREFIX + characterId, "refreshRemain", -1);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 배틀 토큰
    // ══════════════════════════════════════════════════════════════════════

    public void saveBattleToken(String token, Long attackerId, Long defenderId) {
        try {
            Map<String, Long> data = Map.of("attackerId", attackerId, "defenderId", defenderId);
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(BATTLE_PREFIX + token, json, Duration.ofMinutes(10));
        } catch (JsonProcessingException e) {
            log.error("배틀 토큰 저장 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Long> getBattleToken(String token) {
        String json = redisTemplate.opsForValue().get(BATTLE_PREFIX + token);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("배틀 토큰 파싱 실패", e);
            return null;
        }
    }

    public void deleteBattleToken(String token) {
        redisTemplate.delete(BATTLE_PREFIX + token);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 매칭 상대 리스트 캐시
    // ══════════════════════════════════════════════════════════════════════

    public void cacheOpponentList(Long characterId, List<Long> opponentIds) {
        String key = LIST_PREFIX + characterId;
        redisTemplate.delete(key);
        for (Long id : opponentIds) {
            redisTemplate.opsForList().rightPush(key, id.toString());
        }
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    public List<Long> getCachedOpponentList(Long characterId) {
        String key = LIST_PREFIX + characterId;
        List<String> list = redisTemplate.opsForList().range(key, 0, -1);
        if (list == null || list.isEmpty()) return null;
        return list.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    public void deleteCachedOpponentList(Long characterId) {
        redisTemplate.delete(LIST_PREFIX + characterId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 서버 시작 시 초기화
    // ══════════════════════════════════════════════════════════════════════

    public void clearAllPvpData() {
        Set<String> rankingKey = redisTemplate.keys(PVP_RANKING_KEY);
        if (rankingKey != null && rankingKey.isEmpty() == false) redisTemplate.delete(rankingKey);
        redisTemplate.delete(PVP_RANKING_SNAPSHOT_KEY);

        Set<String> infoKeys = redisTemplate.keys(PVP_INFO_PREFIX + "*");
        if (infoKeys != null && infoKeys.isEmpty() == false) redisTemplate.delete(infoKeys);

        Set<String> listKeys = redisTemplate.keys(LIST_PREFIX + "*");
        if (listKeys != null && listKeys.isEmpty() == false) redisTemplate.delete(listKeys);

        clearRankNameData();
    }

    public void clearZoneRankingData() {
        redisTemplate.delete(ZONE_RANKING_KEY);
        redisTemplate.delete(ZONE_RANKING_SNAPSHOT_KEY);
    }

    /** zone:ranking → zone:ranking:snapshot 복사 (랭킹 보드용 주기 스냅샷) */
    public void snapshotZoneRanking() {
        redisTemplate.delete(ZONE_RANKING_SNAPSHOT_KEY);
        redisTemplate.opsForZSet().unionAndStore(ZONE_RANKING_KEY, Collections.emptyList(), ZONE_RANKING_SNAPSHOT_KEY);
    }

    public long getTotalZoneSnapshotCount() {
        Long count = redisTemplate.opsForZSet().zCard(ZONE_RANKING_SNAPSHOT_KEY);
        return count != null ? count : 0L;
    }

    public LinkedHashMap<Long, Integer> getZoneSnapshotPage(int offset, int limit) {
        return buildLongScorePage(ZONE_RANKING_SNAPSHOT_KEY, offset, limit);
    }

    public Long getZoneSnapshotRank(Long characterId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(ZONE_RANKING_SNAPSHOT_KEY, characterId.toString());
        return rank != null ? rank + 1 : null;
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private LinkedHashMap<Long, Integer> buildLongScorePage(String key, int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, offset, (long) offset + limit - 1);

        LinkedHashMap<Long, Integer> map = new LinkedHashMap<>();
        if (result == null) return map;

        for (ZSetOperations.TypedTuple<String> tuple : result) {
            if (tuple.getValue() == null) continue;
            Integer score = tuple.getScore() != null ? tuple.getScore().intValue() : 0;
            map.put(Long.parseLong(tuple.getValue()), score);
        }
        return map;
    }
}
