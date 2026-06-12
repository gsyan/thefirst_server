// 존 클리어/수확/하트비트 서비스
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.ClearZoneStageRequest;
import com.bk.sbs.dto.ClearZoneStageResponse;
import com.bk.sbs.dto.ClaimZoneRewardRequest;
import com.bk.sbs.dto.ClaimZoneRewardResponse;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.ClearedZone;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.ClearedZoneRepository;
import com.bk.sbs.repository.VipSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ZoneService {

    @Value("${heartbeat.throttle-seconds:30}")
    private long heartbeatThrottleSeconds;

    @Value("${zone.require-previous-stage-cleared:true}")
    private boolean requirePreviousStageClearedCheck;

    private final CharacterRepository characterRepository;
    private final ClearedZoneRepository clearedZoneRepository;
    private final GameDataService gameDataService;
    private final RedisService redisService;
    private final VipSubscriptionRepository vipSubscriptionRepository;

    public ZoneService(CharacterRepository characterRepository, ClearedZoneRepository clearedZoneRepository,
                       GameDataService gameDataService, RedisService redisService,
                       VipSubscriptionRepository vipSubscriptionRepository) {
        this.characterRepository = characterRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.gameDataService = gameDataService;
        this.redisService = redisService;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
    }

    // 클리어 기록, rewardClaimed 리셋, 보상은 claimZoneReward에서 별도 처리
    @Transactional
    public ClearZoneStageResponse clearZoneStage(Long characterId, ClearZoneStageRequest request) {
        String zoneName = request.getZoneName();
        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_ZONE_NOT_FOUND);

        int[] parsed = parseZoneName(zoneName);
        int group = parsed[0];
        int stage = parsed[1];
        if (requirePreviousStageClearedCheck == true) {
            if (stage > 1) {
                String prevStageName = group + "-" + (stage - 1);
                if (clearedZoneRepository.existsByCharacterIdAndZoneName(characterId, prevStageName) == false)
                    throw new BusinessException(ServerErrorCode.ZONE_PREVIOUS_STAGE_NOT_CLEARED);
            } else if (group > 1) {
                int maxPrevStage = gameDataService.getZoneConfig().getMaxStageInGroup(group - 1);
                if (maxPrevStage > 0) {
                    String prevStageName = (group - 1) + "-" + maxPrevStage;
                    if (clearedZoneRepository.existsByCharacterIdAndZoneName(characterId, prevStageName) == false)
                        throw new BusinessException(ServerErrorCode.ZONE_PREVIOUS_STAGE_NOT_CLEARED);
                }
            }
        }

        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_CHARACTER_NOT_FOUND));

        // 클라 전투 소모 후 잔액을 서버에 반영
        if (request.getMineralRemain() != null) {
            int mineralRemain = Math.max(0, request.getMineralRemain());
            if (mineralRemain > character.getMineral())
                throw new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_MINERAL_EXCEED_SERVER);
            character.setMineral(mineralRemain);
            characterRepository.save(character);
        }

        boolean isFirstClear = clearedZoneRepository.existsByCharacterIdAndZoneName(characterId, zoneName) == false;
        if (isFirstClear == true) {
            clearedZoneRepository.save(new ClearedZone(characterId, zoneName)); // rewardClaimed=false, firstBonusClaimed=false

            List<String> allZoneNames = clearedZoneRepository.findZoneNamesByCharacterId(characterId);
            allZoneNames.add(zoneName);
            long maxScore = allZoneNames.stream().mapToLong(this::computeZoneScore).max().orElse(0L);
            redisService.setZoneScore(characterId, maxScore);
            redisService.setRankName(characterId, character.getCharacterName());
        } else {
            clearedZoneRepository.resetRewardClaimed(characterId, zoneName); // 재도전: rewardClaimed=false 리셋
        }

        return ClearZoneStageResponse.builder()
                .isFirstClear(isFirstClear)
                .clearedZoneName(isFirstClear ? zoneName : null)
                .build();
    }

    // 보상 지급 — rewardClaimed==true면 중복 요청으로 차단
    // mineral은 매 클리어, techPoint/modulePoint는 firstBonusClaimed==false 일 때만
    @Transactional
    public ClaimZoneRewardResponse claimZoneReward(Long characterId, ClaimZoneRewardRequest request) {
        String zoneName = request.getZoneName();
        boolean watchedAd = request.getWatchedAd() != null && request.getWatchedAd();

        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_ZONE_NOT_FOUND);

        Optional<ClearedZone> clearedZoneOpt = clearedZoneRepository.findByCharacterIdAndZoneName(characterId, zoneName);
        if (clearedZoneOpt.isPresent() == false)
            throw new BusinessException(ServerErrorCode.ZONE_NOT_CLEARED);

        ClearedZone clearedZone = clearedZoneOpt.get();
        if (clearedZone.isRewardClaimed() == true)
            throw new BusinessException(ServerErrorCode.ZONE_REWARD_ALREADY_CLAIMED);

        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_CHARACTER_NOT_FOUND));

        boolean isVip = vipSubscriptionRepository.findByCharacterId(characterId)
                .map(sub -> sub.getVipExpiry() != null && Instant.now().isBefore(sub.getVipExpiry()))
                .orElse(false);
        // VIP: *4, 비VIP+광고: *2, 비VIP: *1
        int multiplier = isVip ? 4 : (watchedAd ? 2 : 1);
        int mineralReward = zoneConfig.getMineralClearReward() * multiplier;
        character.setMineral(character.getMineral() + mineralReward);

        if (clearedZone.isFirstBonusClaimed() == false) {
            character.setTechPoint(character.getTechPoint() + zoneConfig.getTechPointClearReward());
            character.setModulePoint(character.getModulePoint() + zoneConfig.getModulePointClearReward());
            character.setModulePointMaxGot(character.getModulePointMaxGot() + zoneConfig.getModulePointClearReward());
            clearedZone.setFirstBonusClaimed(true);
        }

        clearedZone.setRewardClaimed(true);
        clearedZoneRepository.save(clearedZone);
        characterRepository.save(character);

        return ClaimZoneRewardResponse.builder()
                .zoneName(zoneName)
                .watchedAd(watchedAd)
                .mineralRemain(character.getMineral())
                .techPointRemain(character.getTechPoint())
                .modulePointRemain(character.getModulePoint())
                .modulePointMaxGot(character.getModulePointMaxGot())
                .build();
    }

    private long computeZoneScore(String zoneName) {
        int[] p = parseZoneName(zoneName);
        return (long) p[0] * 1000 + p[1];
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

    public GetStageEnemiesResponse getStageEnemies(GetStageEnemiesRequest request) {
        String zoneName = request.getZoneName();
        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_ZONE_NOT_FOUND);

        return GetStageEnemiesResponse.builder()
                .zoneName(zoneName)
                .enemyFleet(zoneConfig.getEnemyFleet())
                .build();
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long characterId) {
        Instant now = Instant.now();
        characterRepository.updateLastOnlineAtIfStale(characterId, now, now.minusSeconds(heartbeatThrottleSeconds));
        return HeartbeatResponse.builder().build();
    }
}
