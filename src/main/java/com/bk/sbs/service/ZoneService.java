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


    // dev 치트용: 다음 레벨에 필요한 exp만큼만 채워서 정확히 1레벨 증가, 결과 commanderLevel 반환
    @Transactional
    public int addOneCommanderLevel(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DEV_ADD_RESOURCE_FAIL_COMMANDER_NOT_FOUND));

        int requiredExp = gameDataService.getCommanderLevelRequiredExp(commander.getCommanderLevel() + 1);
        if (requiredExp > 0 && commander.getExp() < requiredExp)
            commander.setExp(requiredExp);

        autoLevelUpIfNeeded(commander);
        commanderRepository.save(commander);
        return commander.getCommanderLevel();
    }

    // 코드 입력 등으로 목표 레벨까지 즉시 설정, 이미 그 이상이면 무시 (낮추지 않음)
    @Transactional
    public int setCommanderLevelAtLeast(Long commanderId, int targetLevel) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.REDEEM_CODE_FAIL_COMMANDER_NOT_FOUND));

        if (commander.getCommanderLevel() >= targetLevel)
            return commander.getCommanderLevel();

        int requiredExp = gameDataService.getCommanderLevelRequiredExp(targetLevel);
        if (requiredExp > 0 && commander.getExp() < requiredExp)
            commander.setExp(requiredExp);

        autoLevelUpIfNeeded(commander);
        commanderRepository.save(commander);
        return commander.getCommanderLevel();
    }

    // exp 누적 기준으로 레벨업 조건 판정 후 자동 승급 (레벨업 모듈포인트 보상 개념은 삭제됨)
    private void autoLevelUpIfNeeded(Commander commander) {
        int currentLevel = commander.getCommanderLevel();
        int accumulatedExp = commander.getExp();
        int nextLevel = currentLevel + 1;
        int requiredExp = gameDataService.getCommanderLevelRequiredExp(nextLevel);
        while (requiredExp > 0 && accumulatedExp >= requiredExp) {
            currentLevel = nextLevel;
            nextLevel = currentLevel + 1;
            requiredExp = gameDataService.getCommanderLevelRequiredExp(nextLevel);
        }
        commander.setCommanderLevel(currentLevel);
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long commanderId) {
        Instant now = Instant.now();
        commanderRepository.updateLastOnlineAtIfStale(commanderId, now, now.minusSeconds(heartbeatThrottleSeconds));
        return HeartbeatResponse.builder().build();
    }
}







