// 존 클리어/수확/하트비트 서비스 — 적 수복 시스템(zone 2+, 24h), 클리어된 존 입장 차단 포함
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.ClearZoneStageRequest;
import com.bk.sbs.dto.ClearZoneStageResponse;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.ClearedZone;
import com.bk.sbs.entity.Fleet;
import com.bk.sbs.entity.ModuleResearch;
import com.bk.sbs.entity.ZoneMeta;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.ClearedZoneRepository;
import com.bk.sbs.repository.FleetRepository;
import com.bk.sbs.repository.ModuleResearchRepository;
import com.bk.sbs.repository.ShipRepository;
import com.bk.sbs.repository.ZoneMetaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ZoneService {

    @Value("${heartbeat.throttle-seconds:30}")
    private long heartbeatThrottleSeconds;

    private final CharacterRepository characterRepository;
    private final ClearedZoneRepository clearedZoneRepository;
    private final FleetRepository fleetRepository;
    private final ShipRepository shipRepository;
    private final ModuleResearchRepository moduleResearchRepository;
    private final ZoneMetaRepository zoneMetaRepository;
    private final GameDataService gameDataService;
    private final RedisService redisService;

    public ZoneService(CharacterRepository characterRepository, ClearedZoneRepository clearedZoneRepository,
                       FleetRepository fleetRepository, ShipRepository shipRepository,
                       ModuleResearchRepository moduleResearchRepository,
                       ZoneMetaRepository zoneMetaRepository,
                       GameDataService gameDataService, RedisService redisService) {
        this.characterRepository = characterRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.fleetRepository = fleetRepository;
        this.shipRepository = shipRepository;
        this.moduleResearchRepository = moduleResearchRepository;
        this.zoneMetaRepository = zoneMetaRepository;
        this.gameDataService = gameDataService;
        this.redisService = redisService;
    }

    /**
     * 존 클리어 보고 — 최초 클리어 시 1시간 수집치 보상 지급 + 미수집 자원 정산
     *
     * 케이스1) 이미 클리어된 존(isRestored=false): 입장 차단 에러
     * 케이스2) 수복된 존(isRestored=true): 재클리어 처리 (isRestored=false 복구, 보상 없음)
     * 케이스3) 미클리어 존: 1시간 수집치 보상 + 미수집 자원 정산 + DB 클리어 저장
     */
    @Transactional
    public ClearZoneStageResponse clearZoneStage(Long characterId, ClearZoneStageRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_CHARACTER_NOT_FOUND));

        String zoneName = request.getZoneName();
        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_ZONE_NOT_FOUND);

        List<ClearedZone> clearedZoneEntities = clearedZoneRepository.findByCharacterId(characterId);
        ClearedZone existing = clearedZoneEntities.stream()
                .filter(cz -> cz.getZoneName().equals(zoneName))
                .findFirst().orElse(null);

        // 케이스1: 이미 클리어된 존 (수복되지 않은 상태) — 입장 자체가 차단됐어야 하므로 에러
        if (existing != null && existing.isRestored() == false)
            throw new BusinessException(ServerErrorCode.ZONE_ALREADY_CLEARED);

        Instant now = Instant.now();
        List<String> activeZoneNames = clearedZoneEntities.stream()
                .filter(cz -> cz.isRestored() == false)
                .map(ClearedZone::getZoneName)
                .collect(java.util.stream.Collectors.toList());

        // 케이스2: 수복된 존 재클리어 — isRestored 복구, 보상 없음
        if (existing != null && existing.isRestored() == true) {
            existing.setRestored(false);
            existing.setRestoredAt(null);
            clearedZoneRepository.save(existing);

            activeZoneNames.add(zoneName);
            updateRedisZoneScore(characterId, character, clearedZoneEntities);

            return ClearZoneStageResponse.builder()
                    .rewardInfo(buildRewardInfo(character, new long[]{0L, 0L, 0L, 0L}))
                    .isZoneCleared(true)
                    .clearedZoneName(zoneName)
                    .collectDateTime(character.getCollectDateTime() != null ? character.getCollectDateTime().toString() : null)
                    .build();
        }

        // 케이스3: 최초 클리어 — 이전 미수집 자원 정산 후 클리어 처리
        long offlineCap = calcOfflineCapSeconds(characterId);
        long elapsedSeconds = character.getCollectDateTime() != null
                ? Math.min(ChronoUnit.SECONDS.between(character.getCollectDateTime(), now), offlineCap) : 0L;
        long[] collectRewards = collectZoneResourcesOnline(character, activeZoneNames, elapsedSeconds);

        // 클리어 보상: 해당 존의 1시간 수집치
        long[] clearRewards = calculateClearReward(zoneConfig);
        character.setMineral(character.getMineral() + clearRewards[0]);
        character.setMineralRare(character.getMineralRare() + clearRewards[1]);
        character.setMineralExotic(character.getMineralExotic() + clearRewards[2]);
        character.setMineralDark(character.getMineralDark() + clearRewards[3]);

        clearedZoneRepository.save(new ClearedZone(characterId, zoneName));
        character.setCollectDateTime(now);
        characterRepository.save(character);

        // zone 2+ 최초 클리어 시 ZoneMeta.enemyRestoreTime 세팅
        int[] parts = parseZoneName(zoneName);
        if (parts[0] >= 2) {
            ZoneMeta meta = zoneMetaRepository.findByCharacterId(characterId)
                    .orElse(new ZoneMeta(characterId, null));
            if (meta.getEnemyRestoreTime() == null) {
                meta.setEnemyRestoreTime(now);
                zoneMetaRepository.save(meta);
            }
        }

        // Redis 랭킹 갱신 — isRestored 포함 전체 존 기준 (최고 기록 보장)
        clearedZoneEntities.add(new ClearedZone(characterId, zoneName));
        updateRedisZoneScore(characterId, character, clearedZoneEntities);

        long[] totalRewards = {
            clearRewards[0] + collectRewards[0],
            clearRewards[1] + collectRewards[1],
            clearRewards[2] + collectRewards[2],
            clearRewards[3] + collectRewards[3]
        };

        return ClearZoneStageResponse.builder()
                .rewardInfo(buildRewardInfo(character, totalRewards))
                .isZoneCleared(true)
                .clearedZoneName(zoneName)
                .collectDateTime(now.toString())
                .build();
    }

    // Redis 랭킹 갱신 — isRestored 포함 전체 클리어 존에서 max 계산 (수복 후에도 최고 기록 유지)
    private void updateRedisZoneScore(Long characterId, Character character, List<ClearedZone> allCleared) {
        long maxScore = allCleared.stream()
                .mapToLong(cz -> { int[] p = parseZoneName(cz.getZoneName()); return (long) p[0] * 1000 + p[1]; })
                .max().orElse(0L);
        redisService.setZoneScore(characterId, maxScore);
        redisService.setRankName(characterId, character.getCharacterName());
    }

    @Transactional
    public ZoneCollectResponse collectZone(Long characterId, ZoneCollectRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_CHARACTER_NOT_FOUND));

        // isRestored=false인 존만 수확 대상
        List<String> clearedZoneNames = clearedZoneRepository.findZoneNamesByCharacterId(characterId);
        if (clearedZoneNames.isEmpty()) {
            throw new BusinessException(ServerErrorCode.ZONE_COLLECT_FAIL_NO_CLEARED_ZONE);
        }

        Instant lastCollectTime = character.getCollectDateTime();
        Instant now = Instant.now();
        if (lastCollectTime == null) lastCollectTime = now.minusSeconds(1);

        long offlineCap = calcOfflineCapSeconds(characterId);
        long cappedSeconds = Math.min(ChronoUnit.SECONDS.between(lastCollectTime, now), offlineCap);
        if (cappedSeconds <= 0) {
            return ZoneCollectResponse.builder()
                    .collectDateTime(lastCollectTime.toString())
                    .rewardInfo(CostRemainInfoDto.builder()
                            .mineralCost(0L).mineralRareCost(0L)
                            .mineralExoticCost(0L).mineralDarkCost(0L)
                            .remainMineral(character.getMineral())
                            .remainMineralRare(character.getMineralRare())
                            .remainMineralExotic(character.getMineralExotic())
                            .remainMineralDark(character.getMineralDark())
                            .build())
                    .build();
        }

        long[] rewards = collectZoneResourcesOnline(character, clearedZoneNames, cappedSeconds);
        character.setCollectDateTime(now);
        characterRepository.save(character);

        return ZoneCollectResponse.builder()
                .collectDateTime(now.toString())
                .rewardInfo(buildRewardInfo(character, rewards))
                .build();
    }

    // 기술레벨 기반 오프라인 캡(초) 계산 — DataTableResearch의 stackTime 사용
    private long calcOfflineCapSeconds(Long characterId) {
        List<ModuleResearch> techResearches = moduleResearchRepository
                .findByCharacterIdAndResearchIdStartingWithAndResearchedTrue(characterId, "tech_level_");
        int maxTechLevel = 0;
        for (ModuleResearch r : techResearches) {
            try {
                int level = Integer.parseInt(r.getResearchId().substring("tech_level_".length()));
                if (level > maxTechLevel) maxTechLevel = level;
            } catch (NumberFormatException ignored) { }
        }
        return gameDataService.getStackTimeSeconds(maxTechLevel);
    }

    private long[] collectZoneResourcesOnline(Character character, List<String> clearedZoneNames, long elapsedSeconds) {
        long[] rewards = {0L, 0L, 0L, 0L};
        if (clearedZoneNames.isEmpty() || elapsedSeconds <= 0) { resetFractions(character); return rewards; }

        List<ZoneConfigData> clearedZones = gameDataService.getZoneConfigsByNames(clearedZoneNames);
        if (clearedZones.isEmpty()) { resetFractions(character); return rewards; }

        double totalM = 0, totalMR = 0, totalME = 0, totalMD = 0;
        for (ZoneConfigData z : clearedZones) {
            totalM  += z.getMineralPerHour();
            totalMR += z.getMineralRarePerHour();
            totalME += z.getMineralExoticPerHour();
            totalMD += z.getMineralDarkPerHour();
        }

        double mTotal  = character.getMineralFraction()       + (totalM  / 3600.0 * elapsedSeconds);
        double mrTotal = character.getMineralRareFraction()   + (totalMR / 3600.0 * elapsedSeconds);
        double meTotal = character.getMineralExoticFraction() + (totalME / 3600.0 * elapsedSeconds);
        double mdTotal = character.getMineralDarkFraction()   + (totalMD / 3600.0 * elapsedSeconds);

        rewards[0] = (long) mTotal;  rewards[1] = (long) mrTotal;
        rewards[2] = (long) meTotal; rewards[3] = (long) mdTotal;

        character.setMineral(character.getMineral() + rewards[0]);
        character.setMineralRare(character.getMineralRare() + rewards[1]);
        character.setMineralExotic(character.getMineralExotic() + rewards[2]);
        character.setMineralDark(character.getMineralDark() + rewards[3]);
        resetFractions(character);
        return rewards;
    }

    private void resetFractions(Character character) {
        character.setMineralFraction(0.0);
        character.setMineralRareFraction(0.0);
        character.setMineralExoticFraction(0.0);
        character.setMineralDarkFraction(0.0);
    }

    private CostRemainInfoDto buildRewardInfo(Character character, long[] rewards) {
        return CostRemainInfoDto.builder()
                .mineralCost(-rewards[0])
                .mineralRareCost(-rewards[1])
                .mineralExoticCost(-rewards[2])
                .mineralDarkCost(-rewards[3])
                .remainMineral(character.getMineral())
                .remainMineralRare(character.getMineralRare())
                .remainMineralExotic(character.getMineralExotic())
                .remainMineralDark(character.getMineralDark())
                .build();
    }

    private long[] calculateClearReward(ZoneConfigData zoneConfig) {
        return new long[]{
            zoneConfig.getMineralPerHour()       != null ? zoneConfig.getMineralPerHour().longValue()       : 0L,
            zoneConfig.getMineralRarePerHour()   != null ? zoneConfig.getMineralRarePerHour().longValue()   : 0L,
            zoneConfig.getMineralExoticPerHour() != null ? zoneConfig.getMineralExoticPerHour().longValue() : 0L,
            zoneConfig.getMineralDarkPerHour()   != null ? zoneConfig.getMineralDarkPerHour().longValue()   : 0L
        };
    }

    private int[] parseZoneName(String zoneName) {
        if (zoneName == null || zoneName.isEmpty()) return new int[]{0, 0};
        String[] parts = zoneName.split("-");
        if (parts.length != 2) return new int[]{0, 0};
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return new int[]{0, 0};
        }
    }

    // 클리어 이력 조회 — isRestored 무관, 한 번이라도 클리어했으면 true
    public boolean checkEverCleared(Long characterId, String zoneName) {
        return clearedZoneRepository.existsByCharacterIdAndZoneName(characterId, zoneName);
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long characterId) {
        Instant now = Instant.now();
        characterRepository.updateLastOnlineAtIfStale(characterId, now, now.minusSeconds(heartbeatThrottleSeconds));
        return HeartbeatResponse.builder().build();
    }
}
