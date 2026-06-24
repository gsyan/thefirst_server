// 존 클리어/수확/하트비트 서비스
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.dto.ClearZoneStageRequest;
import com.bk.sbs.dto.ClearZoneStageResponse;
import com.bk.sbs.dto.ClaimZoneRewardRequest;
import com.bk.sbs.dto.ClaimZoneRewardResponse;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.ClearedZone;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderRepository;
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

    private final CommanderRepository commanderRepository;
    private final ClearedZoneRepository clearedZoneRepository;
    private final GameDataService gameDataService;
    private final RedisService redisService;
    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final FleetService fleetService;

    public ZoneService(CommanderRepository commanderRepository, ClearedZoneRepository clearedZoneRepository,
                       GameDataService gameDataService, RedisService redisService,
                       VipSubscriptionRepository vipSubscriptionRepository,
                       FleetService fleetService) {
        this.commanderRepository = commanderRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.gameDataService = gameDataService;
        this.redisService = redisService;
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.fleetService = fleetService;
    }

    // 클리어 기록, rewardClaimed 리셋, 보상은 claimZoneReward에서 별도 처리
    @Transactional
    public ClearZoneStageResponse clearZoneStage(Long commanderId, ClearZoneStageRequest request) {
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
                if (clearedZoneRepository.existsByCommanderIdAndZoneName(commanderId, prevStageName) == false)
                    throw new BusinessException(ServerErrorCode.ZONE_PREVIOUS_STAGE_NOT_CLEARED);
            } else if (group > 1) {
                int maxPrevStage = gameDataService.getZoneConfig().getMaxStageInGroup(group - 1);
                if (maxPrevStage > 0) {
                    String prevStageName = (group - 1) + "-" + maxPrevStage;
                    if (clearedZoneRepository.existsByCommanderIdAndZoneName(commanderId, prevStageName) == false)
                        throw new BusinessException(ServerErrorCode.ZONE_PREVIOUS_STAGE_NOT_CLEARED);
                }
            }
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_COMMANDER_NOT_FOUND));

        // 클라 전투 소모 후 잔액을 서버에 반영
        if (request.getMineralRemain() != null) {
            int mineralRemain = Math.max(0, request.getMineralRemain());
            if (mineralRemain > commander.getMineral())
                throw new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_MINERAL_EXCEED_SERVER);
            commander.setMineral(mineralRemain);
        }

        commanderRepository.save(commander);

        boolean isFirstClear = clearedZoneRepository.existsByCommanderIdAndZoneName(commanderId, zoneName) == false;
        if (isFirstClear == true) {
            clearedZoneRepository.save(new ClearedZone(commanderId, zoneName)); // rewardClaimed=false, firstBonusClaimed=false

            List<String> allZoneNames = clearedZoneRepository.findZoneNamesByCommanderId(commanderId);
            allZoneNames.add(zoneName);
            long maxScore = allZoneNames.stream().mapToLong(this::computeZoneScore).max().orElse(0L);
            redisService.setZoneScore(commanderId, maxScore);
            redisService.setRankName(commanderId, commander.getCommanderName());
        } else {
            clearedZoneRepository.resetRewardClaimed(commanderId, zoneName); // 재도전: rewardClaimed=false 리셋
        }

        return ClearZoneStageResponse.builder()
                .isFirstClear(isFirstClear)
                .clearedZoneName(isFirstClear ? zoneName : null)
                .mineralRemain(commander.getMineral())
                .build();
    }

    // 보상 지급 — rewardClaimed==true면 중복 요청으로 차단
    // mineral은 매 클리어, techPoint/modulePoint는 firstBonusClaimed==false 일 때만
    @Transactional
    public ClaimZoneRewardResponse claimZoneReward(Long commanderId, ClaimZoneRewardRequest request) {
        String zoneName = request.getZoneName();
        boolean watchedAd = request.getWatchedAd() != null && request.getWatchedAd();

        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null)
            throw new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_ZONE_NOT_FOUND);

        Optional<ClearedZone> clearedZoneOpt = clearedZoneRepository.findByCommanderIdAndZoneName(commanderId, zoneName);
        if (clearedZoneOpt.isPresent() == false)
            throw new BusinessException(ServerErrorCode.ZONE_NOT_CLEARED);

        ClearedZone clearedZone = clearedZoneOpt.get();
        if (clearedZone.isRewardClaimed() == true)
            throw new BusinessException(ServerErrorCode.ZONE_REWARD_ALREADY_CLAIMED);

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_COMMANDER_NOT_FOUND));

        boolean isVip = vipSubscriptionRepository.findByCommanderId(commanderId)
                .map(sub -> sub.getVipExpiry() != null && Instant.now().isBefore(sub.getVipExpiry()))
                .orElse(false);
        // VIP: *4, 비VIP+광고: *2, 비VIP: *1
        int multiplier = isVip ? 4 : (watchedAd ? 2 : 1);
        int mineralReward = zoneConfig.getMineralClearReward() * multiplier;
        commander.setMineral(commander.getMineral() + mineralReward);

        if (clearedZone.isFirstBonusClaimed() == false) {
            commander.setTechPoint(commander.getTechPoint() + zoneConfig.getTechPointClearReward());
            commander.setModulePoint(commander.getModulePoint() + zoneConfig.getModulePointClearReward());
            commander.setModulePointMaxGot(commander.getModulePointMaxGot() + zoneConfig.getModulePointClearReward());
            clearedZone.setFirstBonusClaimed(true);
        }

        autoLevelUpIfNeeded(commander);

        // 보상 지급 후 미네랄 세팅 재투입 — 부족 시 초기화
        int totalInvested = fleetService.getTotalInvestedMineral(commanderId);
        boolean mineralSettingReset = false;
        FleetInfoDto updatedFleetInfo = null;
        if (totalInvested > 0) {
            if (commander.getMineral() >= totalInvested) {
                fleetService.deductMineralOnly(commander, totalInvested);
            } else {
                fleetService.resetMineralModules(commanderId);
                mineralSettingReset = true;
                updatedFleetInfo = fleetService.getActiveFleet(commanderId);
            }
        }


        clearedZone.setRewardClaimed(true);
        clearedZoneRepository.save(clearedZone);// 리워드 받았다는 DB 저장
        commanderRepository.save(commander);

        return ClaimZoneRewardResponse.builder()
                .zoneName(zoneName)
                .watchedAd(watchedAd)
                .mineralRemain(commander.getMineral())
                .techPointRemain(commander.getTechPoint())
                .modulePointRemain(commander.getModulePoint())
                .modulePointMaxGot(commander.getModulePointMaxGot())
                .techLevel(commander.getTechLevel())
                .mineralSettingReset(mineralSettingReset)
                .updatedFleetInfo(updatedFleetInfo)
                .build();
    }

    // 재접속 시 DB에 rewardClaimed=false 남은 존 일괄 지급, mineral은 *1 고정
    @Transactional
    public PendingStageRewardResponse claimPendingStageRewards(Long commanderId) {
        List<ClearedZone> pending = clearedZoneRepository.findByCommanderIdAndRewardClaimedFalse(commanderId);

        if (pending.isEmpty()) {
            return PendingStageRewardResponse.builder()
                    .mineralGained(0).techPointGained(0).modulePointGained(0)
                    .mineralRemain(0).techPointRemain(0).modulePointRemain(0).modulePointMaxGot(0)
                    .build();
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_DESTROY_WAVE_FAIL_COMMANDER_NOT_FOUND));

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

        commander.setMineral(commander.getMineral() + mineralGained);
        commander.setTechPoint(commander.getTechPoint() + techPointGained);
        commander.setModulePoint(commander.getModulePoint() + modulePointGained);
        commander.setModulePointMaxGot(commander.getModulePointMaxGot() + modulePointGained);

        autoLevelUpIfNeeded(commander);

        // 보상 지급 후 미네랄 세팅 재투입 — 부족 시 초기화
        int totalInvested = fleetService.getTotalInvestedMineral(commanderId);
        boolean mineralSettingReset = false;
        FleetInfoDto updatedFleetInfo = null;
        if (totalInvested > 0) {
            if (commander.getMineral() >= totalInvested) {
                fleetService.deductMineralOnly(commander, totalInvested);
            } else {
                fleetService.resetMineralModules(commanderId);
                mineralSettingReset = true;
                updatedFleetInfo = fleetService.getActiveFleet(commanderId);
            }
        }

        clearedZoneRepository.saveAll(pending);
        commanderRepository.save(commander);

        return PendingStageRewardResponse.builder()
                .mineralGained(mineralGained)
                .techPointGained(techPointGained)
                .modulePointGained(modulePointGained)
                .mineralRemain(commander.getMineral())
                .techPointRemain(commander.getTechPoint())
                .modulePointRemain(commander.getModulePoint())
                .modulePointMaxGot(commander.getModulePointMaxGot())
                .techLevel(commander.getTechLevel())
                .mineralSettingReset(mineralSettingReset)
                .updatedFleetInfo(updatedFleetInfo)
                .build();
    }

    private long computeZoneScore(String zoneName) {
        int[] p = parseZoneName(zoneName);
        return (long) p[0] * 1000 + p[1];
    }

    // dev 커맨드용: commanderId로 레벨업 재계산 후 저장, 결과 techLevel 반환
    @Transactional
    public int recalcAndSaveTechLevel(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        autoLevelUpIfNeeded(commander);
        commanderRepository.save(commander);
        return commander.getTechLevel();
    }

    // techPoint 누적 기준으로 레벨업 조건 판정 후 자동 승급
    private void autoLevelUpIfNeeded(Commander commander) {
        int currentLevel = commander.getTechLevel();
        int accumulatedPoint = commander.getTechPoint();
        int nextLevel = currentLevel + 1;
        int requiredPoint = gameDataService.getTechLevelRequiredPoint(nextLevel);
        while (requiredPoint > 0 && accumulatedPoint >= requiredPoint) {
            currentLevel = nextLevel;
            nextLevel = currentLevel + 1;
            requiredPoint = gameDataService.getTechLevelRequiredPoint(nextLevel);
        }
        commander.setTechLevel(currentLevel);
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
    public HeartbeatResponse heartbeat(Long commanderId) {
        Instant now = Instant.now();
        commanderRepository.updateLastOnlineAtIfStale(commanderId, now, now.minusSeconds(heartbeatThrottleSeconds));
        return HeartbeatResponse.builder().build();
    }
}







