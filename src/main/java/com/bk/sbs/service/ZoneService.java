package com.bk.sbs.service;

import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.dto.CostRemainInfoDto;
import com.bk.sbs.dto.DestroyZoneStageWaveRequest;
import com.bk.sbs.dto.DestroyZoneStageWaveResponse;
import com.bk.sbs.dto.ExitZoneRequest;
import com.bk.sbs.dto.ExitZoneResponse;
import com.bk.sbs.dto.ZoneCollectRequest;
import com.bk.sbs.dto.ZoneCollectResponse;
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

    /**
     * 웨이브 1개 처치 보고 — 킬 보상 지급 + 미클리어 스테이지는 카운트 누적 후 클리어 판정
     *
     * 케이스1) 이미 클리어된 존: 킬 보상만 지급, isZoneCleared = false
     * 케이스2) 미클리어 존: Redis waveCount 누적, 충족 시 DB 클리어 저장 + isZoneCleared = true
     */
    @Transactional
    public DestroyZoneStageWaveResponse destroyZoneStageWave(Long characterId, DestroyZoneStageWaveRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_CHARACTER_NOT_FOUND));

        String zoneName = request.getZoneName();
        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_ZONE_NOT_FOUND);

        // 킬 보상 계산 (케이스1, 2 공통)
        long[] killRewards = calculateKillRewards(zoneConfig);
        character.setMineral(character.getMineral() + killRewards[0]);
        character.setMineralRare(character.getMineralRare() + killRewards[1]);
        character.setMineralExotic(character.getMineralExotic() + killRewards[2]);
        character.setMineralDark(character.getMineralDark() + killRewards[3]);

        List<String> clearedZoneNames = clearedZoneRepository.findZoneNamesByCharacterId(characterId);

        // 케이스1: 이미 클리어된 존 — 보상만 지급
        if (clearedZoneNames.contains(zoneName)) {
            characterRepository.save(character);
            return DestroyZoneStageWaveResponse.builder()
                    .rewardInfo(buildRewardInfo(character, killRewards))
                    .isZoneCleared(false)
                    .build();
        }

        // 케이스2: 미클리어 존 — waveIndex 검증 후 카운트 누적
        long currentCount = redisService.getZoneWaveCount(characterId, zoneName);
        if (request.getWaveIndex() != (int) currentCount) {
            // waveIndex=0이면 앱 재시작으로 간주 → Redis 리셋 후 재진행
            if (request.getWaveIndex() == 0)
                redisService.deleteZoneWaveCount(characterId, zoneName);
            else
                throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_WAVE_INDEX_MISMATCH);
        }

        long newCount = redisService.incrementZoneWaveCount(characterId, zoneName);

        // 클리어 조건 미달 — 보상만 지급
        if (newCount < zoneConfig.getZoneClearCount()) {
            characterRepository.save(character);
            return DestroyZoneStageWaveResponse.builder()
                    .rewardInfo(buildRewardInfo(character, killRewards))
                    .isZoneCleared(false)
                    .build();
        }

        // 클리어 조건 충족 — 이전 미수집 자원 정산 후 클리어 처리
        Instant now = Instant.now();
        long offlineCap = calcOfflineCapSeconds(characterId);
        long elapsedSeconds = character.getCollectDateTime() != null
                ? Math.min(ChronoUnit.SECONDS.between(character.getCollectDateTime(), now), offlineCap) : 0L;
        long[] collectRewards = collectZoneResourcesOnline(character, clearedZoneNames, elapsedSeconds);

        clearedZoneRepository.save(new ClearedZone(characterId, zoneName));
        character.setCollectDateTime(now);
        characterRepository.save(character);
        redisService.deleteZoneWaveCount(characterId, zoneName);

        // Redis 랭킹 갱신
        clearedZoneNames.add(zoneName);
        long maxScore = clearedZoneNames.stream()
                .mapToLong(z -> { int[] p = parseZoneName(z); return (long) p[0] * 1000 + p[1]; })
                .max().orElse(0L);
        redisService.setZoneScore(characterId, maxScore);
        redisService.setRankName(characterId, character.getCharacterName());

        // 킬 보상 + 수집 보상 합산하여 반환
        long[] totalRewards = {
            killRewards[0] + collectRewards[0],
            killRewards[1] + collectRewards[1],
            killRewards[2] + collectRewards[2],
            killRewards[3] + collectRewards[3]
        };

        return DestroyZoneStageWaveResponse.builder()
                .rewardInfo(buildRewardInfo(character, totalRewards))
                .isZoneCleared(true)
                .clearedZoneName(zoneName)
                .collectDateTime(now.toString())
                .build();
    }

    /** 존 이탈 — 미클리어 스테이지의 웨이브 카운트 초기화 */
    public ExitZoneResponse exitZone(Long characterId, ExitZoneRequest request) {
        redisService.deleteZoneWaveCount(characterId, request.getZoneName());
        return ExitZoneResponse.builder().build();
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

    // 기술레벨 기반 오프라인 캡(초) 계산 — 3h + techLevel/2 시간
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

    private long[] calculateKillRewards(ZoneConfigData zoneConfig) {
        return new long[]{
            zoneConfig.getKillRewardMineral()       != null ? zoneConfig.getKillRewardMineral().longValue()       : 0L,
            zoneConfig.getKillRewardMineralRare()   != null ? zoneConfig.getKillRewardMineralRare().longValue()   : 0L,
            zoneConfig.getKillRewardMineralExotic() != null ? zoneConfig.getKillRewardMineralExotic().longValue() : 0L,
            zoneConfig.getKillRewardMineralDark()   != null ? zoneConfig.getKillRewardMineralDark().longValue()   : 0L
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

    @Transactional
    public HeartbeatResponse heartbeat(Long characterId) {
        Instant now = Instant.now();
        characterRepository.updateLastOnlineAtIfStale(characterId, now, now.minusSeconds(heartbeatThrottleSeconds));
        return HeartbeatResponse.builder().build();
    }
}
