package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Character;
import com.bk.sbs.repository.CharacterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

// PVP / Zone 랭킹 조회, Zone Redis 주기 동기화
@Service
@Slf4j
public class RankingService {

    @Value("${ranking.zone.sync.rate-minutes:60}")
    private long zoneSyncRateMinutes;

    private final RedisService redisService;
    private final CharacterRepository characterRepository;

    public RankingService(RedisService redisService, CharacterRepository characterRepository) {
        this.redisService = redisService;
        this.characterRepository = characterRepository;
    }

    // 서버 시작 시 Zone 랭킹 Redis 초기화 (PvpService @Order(2) 이후)
    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    public void onStartupSyncZoneRanking() {
        syncZoneRankingFromDb();
    }

    // Zone 랭킹 주기 동기화 - DB → zone:ranking 재구축 후 snapshot 갱신
    @Scheduled(fixedRateString = "#{${ranking.zone.sync.rate-minutes:60} * 60000}")
    public void syncZoneRankingFromDb() {
        redisService.clearZoneRankingData();

        List<Character> characters = characterRepository.findAllWithClearedZone();
        for (Character c : characters) {
            long score = computeZoneScore(c.getClearedZone());
            if (score > 0) {
                redisService.setZoneScore(c.getId(), score);
                redisService.setRankName(c.getId(), c.getCharacterName());
            }
        }

        redisService.snapshotZoneRanking();
        String nextUpdatedAt = Instant.now().plusSeconds(zoneSyncRateMinutes * 60).toString();
        redisService.setZoneRankingUpdatedAt(nextUpdatedAt);
        log.info("Zone 랭킹 Redis 동기화 완료: {}건", characters.size());
    }

    // ── PVP 랭킹 ───────────────────────────────────────────────────────────

    public PvpRankingResponse getPvpRanking(int offset, int limit, Long characterId) {
        long totalCount = redisService.getTotalPvpSnapshotCount();
        LinkedHashMap<Long, Integer> page = redisService.getPvpSnapshotPage(offset, limit);

        // DB 조회 없이 rankName Hash에서 일괄 조회
        Set<String> idStrs = new LinkedHashSet<>();
        for (Long id : page.keySet()) idStrs.add(id.toString());
        Map<String, String> nameMap = redisService.getRankNamesMulti(idStrs);

        List<RankingEntryDto> items = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<Long, Integer> entry : page.entrySet()) {
            RankingEntryDto dto = new RankingEntryDto();
            dto.setRank(offset + idx + 1);
            dto.setCharacterId(entry.getKey());
            dto.setCharacterName(nameMap.getOrDefault(entry.getKey().toString(), "Unknown"));
            dto.setScore(String.valueOf(entry.getValue()));
            items.add(dto);
            idx++;
        }

        // 내 정보 계산 (rankName Hash + pvp:info 개인 score)
        RankingEntryDto myInfo = buildMyPvpInfo(characterId);

        String lastUpdatedAt = redisService.getPvpRankingUpdatedAt();

        PvpRankingResponse response = new PvpRankingResponse();
        response.setTotalCount((int) totalCount);
        response.setItems(items);
        response.setMyInfo(myInfo);
        response.setLastUpdatedAt(lastUpdatedAt);
        return response;
    }

    // ── Zone 랭킹 ──────────────────────────────────────────────────────────

    public ZoneRankingResponse getZoneRanking(int offset, int limit, Long characterId) {
        long totalCount = redisService.getTotalZoneSnapshotCount();
        LinkedHashMap<Long, Integer> page = redisService.getZoneSnapshotPage(offset, limit);

        // DB 조회 없이 rankName Hash에서 일괄 조회
        Set<String> idStrs = new LinkedHashSet<>();
        for (Long id : page.keySet()) idStrs.add(id.toString());
        Map<String, String> nameMap = redisService.getRankNamesMulti(idStrs);

        List<RankingEntryDto> items = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<Long, Integer> entry : page.entrySet()) {
            Integer score = entry.getValue();
            int chapter = (int) (score / 1000);
            int stage   = (int) (score % 1000);

            RankingEntryDto dto = new RankingEntryDto();
            dto.setRank(offset + idx + 1);
            dto.setCharacterId(entry.getKey());
            dto.setCharacterName(nameMap.getOrDefault(entry.getKey().toString(), "Unknown"));
            dto.setScore(chapter + "-" + stage);
            items.add(dto);
            idx++;
        }

        // 내 정보 계산
        RankingEntryDto myInfo = buildMyZoneInfo(characterId);

        String lastUpdatedAt = redisService.getZoneRankingUpdatedAt();

        ZoneRankingResponse response = new ZoneRankingResponse();
        response.setTotalCount((int) totalCount);
        response.setItems(items);
        response.setMyInfo(myInfo);
        response.setLastUpdatedAt(lastUpdatedAt);
        return response;
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private RankingEntryDto buildMyPvpInfo(Long characterId) {
        Long rank = redisService.getPvpSnapshotRank(characterId);
        Double scoreD = redisService.getPvpScore(characterId);
        int score = scoreD != null ? scoreD.intValue() : 0;
        String name = redisService.getRankNamesMulti(Collections.singleton(characterId.toString()))
                .getOrDefault(characterId.toString(), "Unknown");

        RankingEntryDto dto = new RankingEntryDto();
        dto.setRank(rank != null ? rank.intValue() : 0);
        dto.setCharacterId(characterId);
        dto.setCharacterName(name);
        dto.setScore(String.valueOf(score));
        return dto;
    }

    private RankingEntryDto buildMyZoneInfo(Long characterId) {
        Long rank = redisService.getZoneSnapshotRank(characterId);
        Double scoreD = redisService.getZoneScore(characterId);
        String name = redisService.getRankNamesMulti(Collections.singleton(characterId.toString()))
                .getOrDefault(characterId.toString(), "Unknown");

        int rawScore = scoreD != null ? scoreD.intValue() : 0;
        int chapter = (int) (rawScore / 1000);
        int stage   = (int) (rawScore % 1000);

        RankingEntryDto dto = new RankingEntryDto();
        dto.setRank(rank != null ? rank.intValue() : 0);
        dto.setCharacterId(characterId);
        dto.setCharacterName(name);
        dto.setScore(rawScore > 0 ? chapter + "-" + stage : "-");
        return dto;
    }

    // "3-5" → 3005
    private long computeZoneScore(String zoneName) {
        String[] parts = zoneName.split("-");
        if (parts.length != 2) return 0L;
        try {
            int chapter = Integer.parseInt(parts[0]);
            int stage   = Integer.parseInt(parts[1]);
            return (long) chapter * 1000 + stage;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
