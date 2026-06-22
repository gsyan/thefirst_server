package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Commander;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.ClearedZoneRepository;
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
    private final CommanderRepository commanderRepository;
    private final ClearedZoneRepository clearedZoneRepository;
    private final FleetService fleetService;
    private final ObjectMapper objectMapper;

    public RankingService(RedisService redisService, CommanderRepository commanderRepository,
                          ClearedZoneRepository clearedZoneRepository, FleetService fleetService,
                          ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.commanderRepository = commanderRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.fleetService = fleetService;
        this.objectMapper = objectMapper;
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

        List<Commander> commanders = commanderRepository.findAllWithClearedZone();
        for (Commander c : commanders) {
            List<String> zoneNames = clearedZoneRepository.findZoneNamesByCommanderId(c.getId());
            long maxScore = zoneNames.stream().mapToLong(this::computeZoneScore).max().orElse(0L);
            if (maxScore > 0) {
                redisService.setZoneScore(c.getId(), maxScore);
                redisService.setRankName(c.getId(), c.getCommanderName());

                FleetInfoDto fleet = fleetService.getActiveFleet(c.getId());
                String statJson = fleetService.computeFleetRankStatJson(fleet);
                if (statJson != null) redisService.setRankStat(c.getId(), statJson);
            }
        }

        redisService.snapshotZoneRanking();
        String nextUpdatedAt = Instant.now().plusSeconds(zoneSyncRateMinutes * 60).toString();
        redisService.setZoneRankingUpdatedAt(nextUpdatedAt);
        log.info("Zone 랭킹 Redis 동기화 완료: {}건", commanders.size());
    }

    // ── PVP 랭킹 ───────────────────────────────────────────────────────────

    public PvpRankingResponse getPvpRanking(int offset, int limit, Long commanderId) {
        long totalCount = redisService.getTotalPvpSnapshotCount();
        LinkedHashMap<Long, Integer> page = redisService.getPvpSnapshotPage(offset, limit);

        // DB 조회 없이 rankName/rankStat Hash에서 일괄 조회
        Set<String> idStrs = new LinkedHashSet<>();
        for (Long id : page.keySet()) idStrs.add(id.toString());
        Map<String, String> nameMap = redisService.getRankNamesMulti(idStrs);
        Map<String, String> statMap = redisService.getRankStatsMulti(idStrs);

        List<RankingEntryDto> items = new ArrayList<>();
        int idx = 0;
        int prevScore = Integer.MIN_VALUE;
        int tieRank = offset + 1;
        for (Map.Entry<Long, Integer> entry : page.entrySet()) {
            int score = entry.getValue();
            if (idx == 0 || score != prevScore) {
                tieRank = offset + idx + 1;
                prevScore = score;
            }
            RankingEntryDto dto = new RankingEntryDto();
            dto.setRank(tieRank);
            dto.setCommanderId(entry.getKey());
            dto.setCommanderName(nameMap.getOrDefault(entry.getKey().toString(), "Unknown"));
            dto.setScore(String.valueOf(score));
            applyStatJson(dto, statMap.get(entry.getKey().toString()));
            items.add(dto);
            idx++;
        }

        // 내 정보 계산 (rankName Hash + pvp:info 개인 score)
        RankingEntryDto myInfo = buildMyPvpInfo(commanderId);

        String lastUpdatedAt = redisService.getPvpRankingUpdatedAt();

        PvpRankingResponse response = new PvpRankingResponse();
        response.setTotalCount((int) totalCount);
        response.setItems(items);
        response.setMyInfo(myInfo);
        response.setLastUpdatedAt(lastUpdatedAt);
        response.setSeasonNumber(redisService.getPvpSeasonNumber());
        response.setSeasonStartTime(redisService.getPvpSeasonStart());
        response.setSeasonEndTime(redisService.getPvpSeasonEnd());
        return response;
    }

    // ── Zone 랭킹 ──────────────────────────────────────────────────────────

    public ZoneRankingResponse getZoneRanking(int offset, int limit, Long commanderId) {
        long totalCount = redisService.getTotalZoneSnapshotCount();
        LinkedHashMap<Long, Integer> page = redisService.getZoneSnapshotPage(offset, limit);

        // DB 조회 없이 rankName/rankStat Hash에서 일괄 조회
        Set<String> idStrs = new LinkedHashSet<>();
        for (Long id : page.keySet()) idStrs.add(id.toString());
        Map<String, String> nameMap = redisService.getRankNamesMulti(idStrs);
        Map<String, String> statMap = redisService.getRankStatsMulti(idStrs);

        List<RankingEntryDto> items = new ArrayList<>();
        int idx = 0;
        int prevScore = Integer.MIN_VALUE;
        int tieRank = offset + 1;
        for (Map.Entry<Long, Integer> entry : page.entrySet()) {
            int score = entry.getValue();
            int chapter = score / 1000;
            int stage   = score % 1000;
            if (idx == 0 || score != prevScore) {
                tieRank = offset + idx + 1;
                prevScore = score;
            }
            RankingEntryDto dto = new RankingEntryDto();
            dto.setRank(tieRank);
            dto.setCommanderId(entry.getKey());
            dto.setCommanderName(nameMap.getOrDefault(entry.getKey().toString(), "Unknown"));
            dto.setScore(chapter + "-" + stage);
            applyStatJson(dto, statMap.get(entry.getKey().toString()));
            items.add(dto);
            idx++;
        }

        // 내 정보 계산
        RankingEntryDto myInfo = buildMyZoneInfo(commanderId);

        String lastUpdatedAt = redisService.getZoneRankingUpdatedAt();

        ZoneRankingResponse response = new ZoneRankingResponse();
        response.setTotalCount((int) totalCount);
        response.setItems(items);
        response.setMyInfo(myInfo);
        response.setLastUpdatedAt(lastUpdatedAt);
        return response;
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private RankingEntryDto buildMyPvpInfo(Long commanderId) {
        Long rank = redisService.getPvpSnapshotRank(commanderId);
        Double scoreD = redisService.getPvpScore(commanderId);
        int score = scoreD != null ? scoreD.intValue() : 0;
        String name = redisService.getRankNamesMulti(Collections.singleton(commanderId.toString()))
                .getOrDefault(commanderId.toString(), "Unknown");

        RankingEntryDto dto = new RankingEntryDto();
        dto.setRank(rank != null ? rank.intValue() : 0);
        dto.setCommanderId(commanderId);
        dto.setCommanderName(name);
        dto.setScore(String.valueOf(score));
        return dto;
    }

    private RankingEntryDto buildMyZoneInfo(Long commanderId) {
        Long rank = redisService.getZoneSnapshotRank(commanderId);
        Double scoreD = redisService.getZoneScore(commanderId);
        String name = redisService.getRankNamesMulti(Collections.singleton(commanderId.toString()))
                .getOrDefault(commanderId.toString(), "Unknown");

        int rawScore = scoreD != null ? scoreD.intValue() : 0;
        int chapter = (int) (rawScore / 1000);
        int stage   = (int) (rawScore % 1000);

        RankingEntryDto dto = new RankingEntryDto();
        dto.setRank(rank != null ? rank.intValue() : 0);
        dto.setCommanderId(commanderId);
        dto.setCommanderName(name);
        dto.setScore(rawScore > 0 ? chapter + "-" + stage : "-");
        return dto;
    }

    private void applyStatJson(RankingEntryDto dto, String statJson) {
        if (statJson == null) return;
        try {
            Map<String, Object> map = objectMapper.readValue(statJson, new TypeReference<Map<String, Object>>() {});
            Object sc = map.get("shipCount");
            Object sh = map.get("statHealth");
            Object sa = map.get("statAttack");
            Object ac = map.get("statAirCount");
            Object aa = map.get("statAirAttack");
            if (sc != null) dto.setShipCount(((Number) sc).intValue());
            if (sh != null) dto.setStatHealth(((Number) sh).floatValue());
            if (sa != null) dto.setStatAttack(((Number) sa).floatValue());
            if (ac != null) dto.setStatAirCount(((Number) ac).intValue());
            if (aa != null) dto.setStatAirAttack(((Number) aa).intValue());
        } catch (Exception e) {
            log.warn("[RankingService] stat JSON 파싱 실패: {}", statJson);
        }
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





