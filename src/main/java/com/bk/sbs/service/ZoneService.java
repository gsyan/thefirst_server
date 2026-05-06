// 존 클리어/수확/하트비트 서비스
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.ClearZoneStageRequest;
import com.bk.sbs.dto.ClearZoneStageResponse;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.ClearedZone;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.ClearedZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ZoneService {

    @Value("${heartbeat.throttle-seconds:30}")
    private long heartbeatThrottleSeconds;

    private final CharacterRepository characterRepository;
    private final ClearedZoneRepository clearedZoneRepository;
    private final GameDataService gameDataService;
    private final RedisService redisService;

    public ZoneService(CharacterRepository characterRepository, ClearedZoneRepository clearedZoneRepository,
                       GameDataService gameDataService, RedisService redisService) {
        this.characterRepository = characterRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.gameDataService = gameDataService;
        this.redisService = redisService;
    }

    @Transactional
    public ClearZoneStageResponse clearZoneStage(Long characterId, ClearZoneStageRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_CHARACTER_NOT_FOUND));

        String zoneName = request.getZoneName();
        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_ZONE_NOT_FOUND);

        if (clearedZoneRepository.existsByCharacterIdAndZoneName(characterId, zoneName))
            throw new BusinessException(ServerErrorCode.ZONE_ALREADY_CLEARED);

        Instant now = Instant.now();
        int clearRewards = calculateClearReward(zoneConfig);
        character.setMineral(character.getMineral() + clearRewards);
        character.setMineralMaxGot(character.getMineralMaxGot() + clearRewards);

        clearedZoneRepository.save(new ClearedZone(characterId, zoneName));
        characterRepository.save(character);

        List<String> allZoneNames = clearedZoneRepository.findZoneNamesByCharacterId(characterId);
        allZoneNames.add(zoneName);
        long maxScore = allZoneNames.stream().mapToLong(this::computeZoneScore).max().orElse(0L);
        redisService.setZoneScore(characterId, maxScore);
        redisService.setRankName(characterId, character.getCharacterName());

        return ClearZoneStageResponse.builder()
                .rewardInfo(buildRewardInfo(character, clearRewards))
                .isZoneCleared(true)
                .clearedZoneName(zoneName)
                .mineralMaxGot(character.getMineralMaxGot())
                .build();
    }

    private long computeZoneScore(String zoneName) {
        int[] p = parseZoneName(zoneName);
        return (long) p[0] * 1000 + p[1];
    }


    private CostRemainInfoDto buildRewardInfo(Character character, int reward) {
        return CostRemainInfoDto.builder()
                .mineralCost(-reward)
                .mineralRemain(character.getMineral())
                .build();
    }

    private int calculateClearReward(ZoneConfigData zoneConfig) {
        return zoneConfig.getMineralClearReward();
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
