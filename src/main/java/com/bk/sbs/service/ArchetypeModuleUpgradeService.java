package com.bk.sbs.service;

import com.bk.sbs.dto.ArchetypeUpgradeInfoDto;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 아키타입 단위 영구 모듈강화(M6) — 업적포인트 소모, 함선 슬롯이 아니라 (커맨더,아키타입,moduleType) 단위로 레벨 귀속
// level 컬럼은 "구매 횟수"(0부터 시작) — 클라에서 쓰는 효과 레벨(datatable_module.csv 티어 행 조회용)은 이 값+1
@Service
public class ArchetypeModuleUpgradeService {

    private final CommanderRepository commanderRepository;
    private final CommanderArchetypeModuleUpgradeRepository upgradeRepository;
    private final GameDataService gameDataService;
    private final HullArchetypeProgressService hullArchetypeProgressService;

    public ArchetypeModuleUpgradeService(CommanderRepository commanderRepository,
                                          CommanderArchetypeModuleUpgradeRepository upgradeRepository,
                                          GameDataService gameDataService,
                                          HullArchetypeProgressService hullArchetypeProgressService) {
        this.commanderRepository = commanderRepository;
        this.upgradeRepository = upgradeRepository;
        this.gameDataService = gameDataService;
        this.hullArchetypeProgressService = hullArchetypeProgressService;
    }

    @Transactional(readOnly = true)
    public List<CommanderArchetypeModuleUpgrade> getUpgrades(Long commanderId) {
        return upgradeRepository.findByCommanderId(commanderId);
    }

    // 클라 응답용 — 이 지휘관의 아키타입 모듈강화 레벨 전체(구매 횟수, slotIndex 포함). 로그인 응답과 PVP 상대 정보가 같은 변환을 씀
    @Transactional(readOnly = true)
    public List<ArchetypeUpgradeInfoDto> getUpgradeDtoList(Long commanderId) {
        return upgradeRepository.findByCommanderId(commanderId).stream()
                .map(u -> ArchetypeUpgradeInfoDto.builder()
                        .hullArchetype(u.getHullArchetype())
                        .moduleType(u.getModuleType())
                        .slotIndex(u.getSlotIndex())
                        .level(u.getLevel())
                        .build())
                .collect(java.util.stream.Collectors.toList());
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

        // 실드/요격체는 패시브로 해금되기 전에는 강화 불가 — 쓰지 못하는 모듈에 업적포인트가 소모되지 않게 막음
        if (moduleType == EModuleType.shield || moduleType == EModuleType.interceptor) {
            Map<String, Set<String>> completedEffects = hullArchetypeProgressService.getCompletedEffectTypesByArchetype(commanderId);
            boolean unlocked = HullArchetypeProgressService.isModuleUnlocked(completedEffects, hullArchetype, moduleType);
            if (unlocked == false)
                throw new BusinessException(ServerErrorCode.UPGRADE_ARCHETYPE_MODULE_FAIL_LOCKED);
        }

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
