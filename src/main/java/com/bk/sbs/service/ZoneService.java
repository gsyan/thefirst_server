package com.bk.sbs.service;

import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.dto.CostRemainInfoDto;
import com.bk.sbs.dto.ZoneClearRequest;
import com.bk.sbs.dto.ZoneClearResponse;
import com.bk.sbs.dto.ZoneCollectRequest;
import com.bk.sbs.dto.ZoneCollectResponse;
import com.bk.sbs.dto.ZoneKillRequest;
import com.bk.sbs.dto.ZoneKillResponse;
import com.bk.sbs.dto.HeartbeatRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ZoneService {

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

        // 이미 클리어된 존 — 이전 미수집 자원만 수집 후 반환
        if (clearedZoneNames.contains(newZoneName)) {
            long[] rewards = collectZoneResources(character, clearedZoneNames, now, true, offlineCap);
            character.setCollectDateTime(now);
            characterRepository.save(character);

            return ZoneClearResponse.builder()
                    .clearedZoneName(newZoneName)
                    .rewardInfo(buildRewardInfo(character, rewards))
                    .collectDateTime(now.toString())
                    .build();
        }

        // 신규 클리어 — 이전 미수집 자원 먼저 collect
        long[] rewards = collectZoneResources(character, clearedZoneNames, now, true, offlineCap);

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

        if (lastCollectTime == null) {
            lastCollectTime = now.minusSeconds(1);
        }

        long elapsedSeconds = ChronoUnit.SECONDS.between(lastCollectTime, now);
        if (elapsedSeconds <= 0) {
            return ZoneCollectResponse.builder()
                    .collectDateTime(lastCollectTime.toString())
                    .onlineSeconds(0L).offlineSeconds(0L).offlineCapSeconds(0L)
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

        long offlineCap = calcOfflineCapSeconds(characterId);
        long[] timeSplit = calcTimeSplitArr(character.getCollectDateTime(), character.getLastOnlineAt(), now, offlineCap);
        long onlineSec = timeSplit[0];
        long offlineSec = timeSplit[1];

        long[][] splitRewards = collectZoneResourcesSplit(character, clearedZoneNames, onlineSec, offlineSec);
        long[] onlineRewards  = splitRewards[0];
        long[] offlineRewards = splitRewards[1];
        long[] totalRewards   = {
            onlineRewards[0] + offlineRewards[0], onlineRewards[1] + offlineRewards[1],
            onlineRewards[2] + offlineRewards[2], onlineRewards[3] + offlineRewards[3]
        };

        character.setCollectDateTime(now);
        characterRepository.save(character);

        CostRemainInfoDto onlineRewardInfo = CostRemainInfoDto.builder()
                .mineralCost(-onlineRewards[0]).mineralRareCost(-onlineRewards[1])
                .mineralExoticCost(-onlineRewards[2]).mineralDarkCost(-onlineRewards[3])
                .remainMineral(0L).remainMineralRare(0L).remainMineralExotic(0L).remainMineralDark(0L)
                .build();

        return ZoneCollectResponse.builder()
                .collectDateTime(now.toString())
                .onlineSeconds(onlineSec)
                .offlineSeconds(offlineSec)
                .offlineCapSeconds(offlineCap)
                .onlineRewardInfo(onlineRewardInfo)
                .rewardInfo(buildRewardInfo(character, totalRewards))
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

    // 공통: 클리어된 존 목록 기반 자원 수집 (반환: [mineral, mineralRare, mineralExotic, mineralDark])
    private long[] collectZoneResources(Character character, List<String> clearedZoneNames, Instant now,
                                         boolean resetFraction, long maxOfflineSeconds) {
        long[] rewards = {0L, 0L, 0L, 0L};

        if (clearedZoneNames.isEmpty() || character.getCollectDateTime() == null) {
            if (resetFraction) resetFractions(character);
            return rewards;
        }

        long elapsedSeconds = calcCreditedSeconds(character.getCollectDateTime(), character.getLastOnlineAt(), now, maxOfflineSeconds);
        if (elapsedSeconds <= 0) {
            if (resetFraction) resetFractions(character);
            return rewards;
        }

        List<ZoneConfigData> clearedZones = gameDataService.getZoneConfigsByNames(clearedZoneNames);
        if (clearedZones.isEmpty()) {
            if (resetFraction) resetFractions(character);
            return rewards;
        }

        double totalM = 0, totalMR = 0, totalME = 0, totalMD = 0;
        for (ZoneConfigData z : clearedZones) {
            totalM  += z.getMineralPerHour();
            totalMR += z.getMineralRarePerHour();
            totalME += z.getMineralExoticPerHour();
            totalMD += z.getMineralDarkPerHour();
        }

        double mTotal  = character.getMineralFraction()      + (totalM  / 3600.0 * elapsedSeconds);
        double mrTotal = character.getMineralRareFraction()  + (totalMR / 3600.0 * elapsedSeconds);
        double meTotal = character.getMineralExoticFraction()+ (totalME / 3600.0 * elapsedSeconds);
        double mdTotal = character.getMineralDarkFraction()  + (totalMD / 3600.0 * elapsedSeconds);

        rewards[0] = (long) mTotal;
        rewards[1] = (long) mrTotal;
        rewards[2] = (long) meTotal;
        rewards[3] = (long) mdTotal;

        character.setMineral(character.getMineral() + rewards[0]);
        character.setMineralRare(character.getMineralRare() + rewards[1]);
        character.setMineralExotic(character.getMineralExotic() + rewards[2]);
        character.setMineralDark(character.getMineralDark() + rewards[3]);

        if (resetFraction) {
            resetFractions(character);
        } else {
            character.setMineralFraction(mTotal  - rewards[0]);
            character.setMineralRareFraction(mrTotal - rewards[1]);
            character.setMineralExoticFraction(meTotal - rewards[2]);
            character.setMineralDarkFraction(mdTotal - rewards[3]);
        }

        return rewards;
    }

    // 온라인/오프라인 시간을 분리하여 반환: [0]=onlineSeconds, [1]=offlineSeconds
    private long[] calcTimeSplitArr(Instant collectDateTime, Instant lastOnlineAt, Instant now, long offlineCap) {
        if (collectDateTime == null) return new long[]{0L, 0L};
        final long GRACE_SECONDS = 60L;

        if (lastOnlineAt == null) {
            long total = Math.max(0L, ChronoUnit.SECONDS.between(collectDateTime, now));
            return new long[]{0L, Math.min(total, offlineCap)};
        }

        long nMinusL = ChronoUnit.SECONDS.between(lastOnlineAt, now);
        if (nMinusL <= GRACE_SECONDS) {
            // 현재 온라인 중 → 전 구간 온라인, 캡 없음
            long total = Math.max(0L, ChronoUnit.SECONDS.between(collectDateTime, now));
            return new long[]{total, 0L};
        }

        long onlineSec  = Math.max(0L, ChronoUnit.SECONDS.between(collectDateTime, lastOnlineAt));
        long offlineSec = Math.min(nMinusL, offlineCap);
        return new long[]{onlineSec, offlineSec};
    }

    // 온라인/오프라인 보상을 분리 계산: [0]=onlineRewards[4], [1]=offlineRewards[4]
    // 분수(fraction)를 온라인→오프라인 순서로 연속 적용하여 정확도 보장
    private long[][] collectZoneResourcesSplit(Character character, List<String> clearedZoneNames,
                                               long onlineSeconds, long offlineSeconds) {
        long[] onlineRewards  = {0L, 0L, 0L, 0L};
        long[] offlineRewards = {0L, 0L, 0L, 0L};

        if (clearedZoneNames.isEmpty() || character.getCollectDateTime() == null
                || onlineSeconds + offlineSeconds <= 0) {
            resetFractions(character);
            return new long[][]{onlineRewards, offlineRewards};
        }

        List<ZoneConfigData> clearedZones = gameDataService.getZoneConfigsByNames(clearedZoneNames);
        if (clearedZones.isEmpty()) {
            resetFractions(character);
            return new long[][]{onlineRewards, offlineRewards};
        }

        double totalM = 0, totalMR = 0, totalME = 0, totalMD = 0;
        for (ZoneConfigData z : clearedZones) {
            totalM  += z.getMineralPerHour();
            totalMR += z.getMineralRarePerHour();
            totalME += z.getMineralExoticPerHour();
            totalMD += z.getMineralDarkPerHour();
        }

        // 온라인 구간: 기존 fraction + 온라인 시간 적립
        double omT  = character.getMineralFraction()       + (totalM  / 3600.0 * onlineSeconds);
        double omrT = character.getMineralRareFraction()   + (totalMR / 3600.0 * onlineSeconds);
        double omeT = character.getMineralExoticFraction() + (totalME / 3600.0 * onlineSeconds);
        double omdT = character.getMineralDarkFraction()   + (totalMD / 3600.0 * onlineSeconds);

        onlineRewards[0] = (long) omT;
        onlineRewards[1] = (long) omrT;
        onlineRewards[2] = (long) omeT;
        onlineRewards[3] = (long) omdT;

        // 오프라인 구간: 온라인 잔여 fraction + 오프라인 시간 적립
        double xmT  = (omT  - onlineRewards[0]) + (totalM  / 3600.0 * offlineSeconds);
        double xmrT = (omrT - onlineRewards[1]) + (totalMR / 3600.0 * offlineSeconds);
        double xmeT = (omeT - onlineRewards[2]) + (totalME / 3600.0 * offlineSeconds);
        double xmdT = (omdT - onlineRewards[3]) + (totalMD / 3600.0 * offlineSeconds);

        offlineRewards[0] = (long) xmT;
        offlineRewards[1] = (long) xmrT;
        offlineRewards[2] = (long) xmeT;
        offlineRewards[3] = (long) xmdT;

        character.setMineral(character.getMineral()           + onlineRewards[0] + offlineRewards[0]);
        character.setMineralRare(character.getMineralRare()   + onlineRewards[1] + offlineRewards[1]);
        character.setMineralExotic(character.getMineralExotic()+ onlineRewards[2] + offlineRewards[2]);
        character.setMineralDark(character.getMineralDark()   + onlineRewards[3] + offlineRewards[3]);

        character.setMineralFraction(xmT  - offlineRewards[0]);
        character.setMineralRareFraction(xmrT - offlineRewards[1]);
        character.setMineralExoticFraction(xmeT - offlineRewards[2]);
        character.setMineralDarkFraction(xmdT - offlineRewards[3]);

        return new long[][]{onlineRewards, offlineRewards};
    }

    // 온라인/오프라인 구간 분리 적립 시간 계산
    // C→L: 온라인 구간(캡 없음), L→N: 오프라인 구간(offlineCap 적용)
    // N-L ≤ 60s(grace)이면 전 구간 온라인 취급
    private long calcCreditedSeconds(Instant collectDateTime, Instant lastOnlineAt, Instant now, long offlineCap) {
        if (collectDateTime == null) return 0L;
        final long GRACE_SECONDS = 60L;

        if (lastOnlineAt == null) {
            // lastOnlineAt 없음 → 전 구간 오프라인 취급
            return Math.min(ChronoUnit.SECONDS.between(collectDateTime, now), offlineCap);
        }

        long nMinusL = ChronoUnit.SECONDS.between(lastOnlineAt, now);
        if (nMinusL <= GRACE_SECONDS) {
            // 현재 온라인 중 → 전 구간 온라인, 캡 없음
            return Math.max(0L, ChronoUnit.SECONDS.between(collectDateTime, now));
        }

        // C→L 온라인(캡 없음) + L→N 오프라인(offlineCap)
        long onlineSeconds = Math.max(0L, ChronoUnit.SECONDS.between(collectDateTime, lastOnlineAt));
        long offlineSeconds = Math.min(nMinusL, offlineCap);
        return onlineSeconds + offlineSeconds;
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
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.HEARTBEAT_FAIL_CHARACTER_NOT_FOUND));
        character.setLastOnlineAt(Instant.now());
        characterRepository.save(character);
        return new HeartbeatResponse();
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
