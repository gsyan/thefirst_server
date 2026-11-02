package com.bk.sbs.service;

import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.UpgradeArchetypeModuleRequest;
import com.bk.sbs.dto.UpgradeArchetypeModuleResponse;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderArchetypeModuleUpgrade;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CommanderArchetypeModuleUpgradeRepository;
import com.bk.sbs.repository.CommanderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// 아키타입 단위 영구 모듈강화(M6) — 업적포인트 소모, 함선 슬롯이 아니라 (커맨더,아키타입,moduleType) 단위로 레벨 귀속
// level 컬럼은 "구매 횟수"(0부터 시작) — 클라에서 쓰는 효과 레벨(datatable_module.csv 티어 행 조회용)은 이 값+1
@Service
public class ArchetypeModuleUpgradeService {

    private final CommanderRepository commanderRepository;
    private final CommanderArchetypeModuleUpgradeRepository upgradeRepository;
    private final GameDataService gameDataService;

    public ArchetypeModuleUpgradeService(CommanderRepository commanderRepository,
                                          CommanderArchetypeModuleUpgradeRepository upgradeRepository,
                                          GameDataService gameDataService) {
        this.commanderRepository = commanderRepository;
        this.upgradeRepository = upgradeRepository;
        this.gameDataService = gameDataService;
    }

    @Transactional(readOnly = true)
    public List<CommanderArchetypeModuleUpgrade> getUpgrades(Long commanderId) {
        return upgradeRepository.findByCommanderId(commanderId);
    }

    @Transactional
    public UpgradeArchetypeModuleResponse upgrade(Long commanderId, UpgradeArchetypeModuleRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_ARCHETYPE_MODULE_FAIL_COMMANDER_NOT_FOUND));

        String hullArchetype = request.getHullArchetype();
        if (hullArchetype == null || isValidHullArchetype(hullArchetype) == false)
            throw new BusinessException(ServerErrorCode.UPGRADE_ARCHETYPE_MODULE_FAIL_INVALID_ARCHETYPE);

        EModuleType moduleType = request.getModuleType();
        int slotIndex = resolveSlotIndex(hullArchetype, moduleType, request.getSlotIndex());

        Optional<CommanderArchetypeModuleUpgrade> existing = upgradeRepository
                .findByCommanderIdAndHullArchetypeAndModuleTypeAndSlotIndex(commanderId, hullArchetype, moduleType, slotIndex);
        int currentLevel = existing.map(CommanderArchetypeModuleUpgrade::getLevel).orElse(0);

        // 효과 레벨 = 구매횟수+1 — 이번 구매로 도달할 레벨은 (현재 구매횟수)+2, 그 레벨 행의 ap_cost를 그대로 비용으로 사용
        int targetLevel = currentLevel + 2;
        Integer cost = gameDataService.getArchetypeLevelUpCost(hullArchetype, moduleType, targetLevel);
        if (cost == null)
            throw new BusinessException(ServerErrorCode.UPGRADE_ARCHETYPE_MODULE_FAIL_MAX_LEVEL_REACHED);

        if (commander.getAchievementPoint() < cost)
            throw new BusinessException(ServerErrorCode.UPGRADE_ARCHETYPE_MODULE_FAIL_INSUFFICIENT_ACHIEVEMENT_POINT);

        commander.setAchievementPoint(commander.getAchievementPoint() - cost);
        commanderRepository.save(commander);

        int newLevel = currentLevel + 1;
        if (existing.isPresent()) {
            CommanderArchetypeModuleUpgrade upgrade = existing.get();
            upgrade.setLevel(newLevel);
            upgradeRepository.save(upgrade);
        } else {
            upgradeRepository.save(new CommanderArchetypeModuleUpgrade(commanderId, hullArchetype, moduleType, slotIndex, newLevel));
        }

        return UpgradeArchetypeModuleResponse.builder()
                .hullArchetype(hullArchetype)
                .moduleType(moduleType)
                .slotIndex(slotIndex)
                .level(newLevel)
                .achievementPointRemain(commander.getAchievementPoint())
                .build();
    }

    // hull/shield/interceptor는 슬롯 개념이 없어 slotIndex를 항상 0으로 강제. beam/missile/hangar는 그 아키타입이 실제로 가진 슬롯 개수 범위로 검증
    private int resolveSlotIndex(String hullArchetype, EModuleType moduleType, Integer requestedSlotIndex) {
        if (moduleType == EModuleType.hull || moduleType == EModuleType.shield || moduleType == EModuleType.interceptor)
            return 0;

        int slotIndex = requestedSlotIndex != null ? requestedSlotIndex : 0;
        int maxSlotCount = gameDataService.getMaxSlotCountForArchetype(hullArchetype, moduleType);
        if (slotIndex < 0 || slotIndex >= maxSlotCount)
            throw new BusinessException(ServerErrorCode.UPGRADE_ARCHETYPE_MODULE_FAIL_INVALID_SLOT_INDEX);

        return slotIndex;
    }

    private boolean isValidHullArchetype(String hullArchetype) {
        for (ModuleData data : gameDataService.getModulesByType(EModuleType.hull)) {
            if (hullArchetype.equals(data.getHullArchetype())) return true;
        }
        return false;
    }
}
