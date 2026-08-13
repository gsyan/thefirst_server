// 존 클리어/수확/하트비트 서비스
package com.bk.sbs.service;

import com.bk.sbs.dto.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.util.CommanderLevelUtil;
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

    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;
    private final RedisService redisService;

    public ZoneService(CommanderRepository commanderRepository,
                       GameDataService gameDataService, RedisService redisService) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.redisService = redisService;
    }


    // dev 치트용: 다음 레벨에 필요한 exp만큼만 채워서 정확히 1레벨 증가, 결과 commanderLevel 반환
    @Transactional
    public int addOneCommanderLevel(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.DEV_ADD_RESOURCE_FAIL_COMMANDER_NOT_FOUND));

        int requiredExp = gameDataService.getCommanderLevelRequiredExp(commander.getCommanderLevel() + 1);
        if (requiredExp > 0 && commander.getExp() < requiredExp)
            commander.setExp(requiredExp);

        CommanderLevelUtil.autoLevelUpIfNeeded(commander, gameDataService);
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

        CommanderLevelUtil.autoLevelUpIfNeeded(commander, gameDataService);
        commanderRepository.save(commander);
        return commander.getCommanderLevel();
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long commanderId) {
        Instant now = Instant.now();
        commanderRepository.updateLastOnlineAtIfStale(commanderId, now, now.minusSeconds(heartbeatThrottleSeconds));
        return HeartbeatResponse.builder().build();
    }
}







