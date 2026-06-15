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
    private final FleetService fleetService;

    public ZoneService(CharacterRepository characterRepository, ClearedZoneRepository clearedZoneRepository,
                       GameDataService gameDataService, RedisService redisService,
                       VipSubscriptionRepository vipSubscriptionRepository,
                       FleetService fleetService) {
        this.characterRepository = characterRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.gameDataService = gameDataService;
        this.redisService = redisService;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.fleetService = fleetService;
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

        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_CHARACTER_NOT_FOUND));

        // 클라 전투 소모 후 잔액을 서버에 반영
        if (request.getMineralRemain() != null) {
            int mineralRemain = Math.max(0, request.getMineralRemain());
            if (mineralRemain > character.getMineral())
                throw new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_MINERAL_EXCEED_SERVER);
            character.setMineral(mineralRemain);
        }

        // 미네랄 강화 초기화 — modulePointSubType/Level 기준값으로 복원, 투자 미네랄 소멸
        fleetService.resetMineralModules(characterId);
        characterRepository.save(character);

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

        FleetInfoDto updatedFleetInfo = fleetService.getActiveFleet(characterId);

        return ClearZoneStageResponse.builder()
                .isFirstClear(isFirstClear)
                .clearedZoneName(isFirstClear ? zoneName : null)
                .updatedFleetInfo(updatedFleetInfo)
                .mineralRemain(character.getMineral())
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

        autoLevelUpIfNeeded(character);

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
                .techLevel(character.getTechLevel())
                .build();
    }

    // 재접속 시 DB에 rewardClaimed=false 남은 존 일괄 지급, mineral은 *1 고정
    @Transactional
    public PendingStageRewardResponse claimPendingStageRewards(Long characterId) {
        List<ClearedZone> pending = clearedZoneRepository.findByCharacterIdAndRewardClaimedFalse(characterId);

        if (pending.isEmpty()) {
            return PendingStageRewardResponse.builder()
                    .mineralGained(0).techPointGained(0).modulePointGained(0)
                    .mineralRemain(0).techPointRemain(0).modulePointRemain(0).modulePointMaxGot(0)
                    .build();
        }

        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_CHARACTER_NOT_FOUND));

        int mineralGained = 0;
        int techPointGained = 0;
        int modulePointGained = 0;

        for (ClearedZone zone : pending) {
            ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zone.getZoneName());
            if (zoneConfig == null) continue;

            mineralGained += zoneConfig.getMineralClearReward();

            if (zone.isFirstBonusClaimed() == false) {
                techPointGained += zoneConfig.getTechPointClearReward();
                modulePointGained += zoneConfig.getModulePointClearReward();
                zone.setFirstBonusClaimed(true);
            }

            zone.setRewardClaimed(true);
        }

        character.setMineral(character.getMineral() + mineralGained);
        character.setTechPoint(character.getTechPoint() + techPointGained);
        character.setModulePoint(character.getModulePoint() + modulePointGained);
        character.setModulePointMaxGot(character.getModulePointMaxGot() + modulePointGained);

        autoLevelUpIfNeeded(character);

        clearedZoneRepository.saveAll(pending);
        characterRepository.save(character);

        return PendingStageRewardResponse.builder()
                .mineralGained(mineralGained)
                .techPointGained(techPointGained)
                .modulePointGained(modulePointGained)
                .mineralRemain(character.getMineral())
                .techPointRemain(character.getTechPoint())
                .modulePointRemain(character.getModulePoint())
                .modulePointMaxGot(character.getModulePointMaxGot())
                .techLevel(character.getTechLevel())
                .build();
    }

    private long computeZoneScore(String zoneName) {
        int[] p = parseZoneName(zoneName);
        return (long) p[0] * 1000 + p[1];
    }

    // dev 커맨드용: characterId로 레벨업 재계산 후 저장, 결과 techLevel 반환
    @Transactional
    public int recalcAndSaveTechLevel(Long characterId) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_CHARACTER_NOT_FOUND));
        autoLevelUpIfNeeded(character);
        characterRepository.save(character);
        return character.getTechLevel();
    }

    // techPoint 누적 기준으로 레벨업 조건 판정 후 자동 승급
    private void autoLevelUpIfNeeded(Character character) {
        int currentLevel = character.getTechLevel();
        int accumulatedPoint = character.getTechPoint();
        int nextLevel = currentLevel + 1;
        int requiredPoint = gameDataService.getTechLevelRequiredPoint(nextLevel);
        while (requiredPoint > 0 && accumulatedPoint >= requiredPoint) {
            currentLevel = nextLevel;
            nextLevel = currentLevel + 1;
            requiredPoint = gameDataService.getTechLevelRequiredPoint(nextLevel);
        }
        character.setTechLevel(currentLevel);
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
