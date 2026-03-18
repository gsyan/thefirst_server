package com.bk.sbs.service;

import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.dto.CostRemainInfoDto;
import com.bk.sbs.dto.ZoneClearRequest;
import com.bk.sbs.dto.ZoneClearResponse;
import com.bk.sbs.dto.ZoneCollectRequest;
import com.bk.sbs.dto.ZoneCollectResponse;
import com.bk.sbs.dto.ZoneKillRequest;
import com.bk.sbs.dto.ZoneKillResponse;
import com.bk.sbs.dto.HeartbeatResponse;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.ClearedZone;
import com.bk.sbs.entity.Fleet;
import com.bk.sbs.entity.ModuleResearch;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.ClearedZoneRepository;
import com.bk.sbs.repository.FleetRepository;
import com.bk.sbs.repository.ModuleResearchRepository;
import com.bk.sbs.repository.ShipRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final GameDataService gameDataService;
    private final RedisService redisService;

    public ZoneService(CharacterRepository characterRepository, ClearedZoneRepository clearedZoneRepository,
                       FleetRepository fleetRepository, ShipRepository shipRepository,
                       ModuleResearchRepository moduleResearchRepository,
                       GameDataService gameDataService, RedisService redisService) {
        this.characterRepository = characterRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.fleetRepository = fleetRepository;
        this.shipRepository = shipRepository;
        this.moduleResearchRepository = moduleResearchRepository;
        this.gameDataService = gameDataService;
        this.redisService = redisService;
    }

    @Transactional
    public ZoneClearResponse clearZone(Long characterId, ZoneClearRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_CHARACTER_NOT_FOUND));

        String newZoneName = request.getZoneName();

        // zone X-Y: 함선 X척 이상 보유 조건 검증
        int[] zoneParts = parseZoneName(newZoneName);
        int requiredShips = zoneParts[0];
        if (requiredShips > 0) {
            Fleet activeFleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                    .orElse(null);
            int shipCount = (activeFleet == null) ? 0
                    : shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(activeFleet.getId()).size();
            if (shipCount < requiredShips)
                throw new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_INSUFFICIENT_SHIPS);
        }

        Instant now = Instant.now();
        List<String> clearedZoneNames = clearedZoneRepository.findZoneNamesByCharacterId(characterId);
        long offlineCap = calcOfflineCapSeconds(characterId);
        long elapsedSeconds = character.getCollectDateTime() != null
                ? Math.min(ChronoUnit.SECONDS.between(character.getCollectDateTime(), now), offlineCap) : 0L;

        // 이미 클리어된 존 — 이전 미수집 자원만 수집 후 반환
        if (clearedZoneNames.contains(newZoneName)) {
            long[] rewards = collectZoneResourcesOnline(character, clearedZoneNames, elapsedSeconds);
            character.setCollectDateTime(now);
            characterRepository.save(character);

            return ZoneClearResponse.builder()
                    .clearedZoneName(newZoneName)
                    .rewardInfo(buildRewardInfo(character, rewards))
                    .collectDateTime(now.toString())
                    .build();
        }

        // 신규 클리어 — 이전 미수집 자원 먼저 수집 (캡 적용)
        long[] rewards = collectZoneResourcesOnline(character, clearedZoneNames, elapsedSeconds);

        // cleared_zone 테이블에 추가
        clearedZoneRepository.save(new ClearedZone(characterId, newZoneName));
        character.setCollectDateTime(now);
        characterRepository.save(character);

        // Redis 랭킹: 클리어된 존 중 가장 높은 점수 기준
        clearedZoneNames.add(newZoneName);
        long maxScore = clearedZoneNames.stream()
                .mapToLong(z -> { int[] p = parseZoneName(z); return (long) p[0] * 1000 + p[1]; })
                .max().orElse(0L);
        redisService.setZoneScore(characterId, maxScore);
        redisService.setRankName(characterId, character.getCharacterName());

        return ZoneClearResponse.builder()
                .clearedZoneName(newZoneName)
                .rewardInfo(buildRewardInfo(character, rewards))
                .collectDateTime(now.toString())
                .build();
    }

    @Transactional
    public ZoneCollectResponse collectZone(Long characterId, ZoneCollectRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_CHARACTER_NOT_FOUND));

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

    // 기술레벨 기반 오프라인 캡(초) 계산 — 3h + techLevel/2 시간, 구독 시 24h (TODO: 구독 구현 시 반영)
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
        long capHours = 3L + (maxTechLevel / 2);
        return capHours * 3600L;
    }

    // 온라인 수확 전용: elapsed seconds 직접 지정, 온/오프 분리 없이 전 구간 적립
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

    // "x-y" 형식 파싱
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

    @Transactional
    public ZoneKillResponse killZone(Long characterId, ZoneKillRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_KILL_FAIL_CHARACTER_NOT_FOUND));

        long[] rewards = calculateKillRewards(request.getZoneName());
        character.setMineral(character.getMineral() + rewards[0]);
        character.setMineralRare(character.getMineralRare() + rewards[1]);
        character.setMineralExotic(character.getMineralExotic() + rewards[2]);
        character.setMineralDark(character.getMineralDark() + rewards[3]);
        characterRepository.save(character);

        return ZoneKillResponse.builder().rewardInfo(buildRewardInfo(character, rewards)).build();
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long characterId) {
        Instant now = Instant.now();
        // 스로틀: 마지막 갱신이 heartbeatThrottleSeconds 이상 지난 경우에만 업데이트
        characterRepository.updateLastOnlineAtIfStale(characterId, now, now.minusSeconds(heartbeatThrottleSeconds));
        return HeartbeatResponse.builder().build();
    }

    private long[] calculateKillRewards(String zoneName) {
        long[] rewards = {0L, 0L, 0L, 0L};
        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null) return rewards;
        rewards[0] = zoneConfig.getKillRewardMineral()      != null ? zoneConfig.getKillRewardMineral().longValue()      : 0L;
        rewards[1] = zoneConfig.getKillRewardMineralRare()  != null ? zoneConfig.getKillRewardMineralRare().longValue()  : 0L;
        rewards[2] = zoneConfig.getKillRewardMineralExotic()!= null ? zoneConfig.getKillRewardMineralExotic().longValue(): 0L;
        rewards[3] = zoneConfig.getKillRewardMineralDark()  != null ? zoneConfig.getKillRewardMineralDark().longValue()  : 0L;
        return rewards;
    }
}
