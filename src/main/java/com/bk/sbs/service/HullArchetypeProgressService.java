package com.bk.sbs.service;

import com.bk.sbs.dto.HullArchetypeProgressInfoDto;
import com.bk.sbs.entity.CommanderHullArchetypeProgress;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.repository.CommanderHullArchetypeProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 아키타입 패시브 단계별 해금 진행도 — 단계는 순서대로 하나씩만 진행됨(엄격한 순차):
// 앞 단계가 완료돼야 다음 단계가 0부터 세기 시작하고, 지금 진행 중인 단계가 세는 카운터 이벤트만 반영되며 나머지 이벤트는 버려짐.
// 한 번의 이벤트가 문턱을 넘겨도 넘친 값은 다음 단계로 이월하지 않고 버림. 문턱 0인 단계(1단계)는 처음부터 완료.
@Service
public class HullArchetypeProgressService {

    // 패시브 효과 종류 중 실드/요격체 해금 — datatable_hull_archetype_passive.csv effect_type 값과 동일
    private static final String k_effectUnlockPrefix = "unlock_";
    private static final String k_effectUnlockShield = "unlock_shield";
    private static final String k_effectUnlockInterceptor = "unlock_interceptor";
    private static final String k_effectUnlockShieldInterceptor = "unlock_shield_interceptor";

    private final CommanderHullArchetypeProgressRepository repository;
    private final GameDataService gameDataService;

    public HullArchetypeProgressService(CommanderHullArchetypeProgressRepository repository, GameDataService gameDataService) {
        this.repository = repository;
        this.gameDataService = gameDataService;
    }

    // 클라 응답용 — 이 지휘관의 아키타입 패시브 단계별 진행도 전체 목록
    @Transactional(readOnly = true)
    public List<HullArchetypeProgressInfoDto> getProgressDtoList(Long commanderId) {
        return repository.findByCommanderId(commanderId).stream()
                .map(p -> HullArchetypeProgressInfoDto.builder()
                        .hullArchetype(p.getHullArchetype())
                        .stage(p.getStage())
                        .counterType(p.getCounterType())
                        .counterValue(p.getCounterValue())
                        .completed(p.isCompleted())
                        .build())
                .collect(Collectors.toList());
    }

    // 지휘관의 아키타입별 "완료된 패시브 단계의 효과 종류" 집합 — 진행 행이 없는 단계도 앞 단계 완료 + 문턱 0이면 완료로 봄(1단계)
    @Transactional(readOnly = true)
    public Map<String, Set<String>> getCompletedEffectTypesByArchetype(Long commanderId) {
        Map<String, Map<Integer, CommanderHullArchetypeProgress>> rowsByArchetype = new HashMap<>();
        for (CommanderHullArchetypeProgress row : repository.findByCommanderId(commanderId))
            rowsByArchetype.computeIfAbsent(row.getHullArchetype(), k -> new HashMap<>()).put(row.getStage(), row);

        Map<String, Set<String>> result = new HashMap<>();
        for (String hullArchetype : gameDataService.getHullArchetypesWithPassives()) {
            Map<Integer, CommanderHullArchetypeProgress> rowByStage = rowsByArchetype.getOrDefault(hullArchetype, Map.of());
            Set<String> effectTypes = new HashSet<>();
            boolean previousCompleted = true;
            for (GameDataService.HullArchetypePassiveEntry entry : gameDataService.getHullArchetypePassives(hullArchetype)) {
                CommanderHullArchetypeProgress row = rowByStage.get(entry.stage);
                boolean rowCompleted = row != null && row.isCompleted() == true;
                boolean autoCompleted = previousCompleted == true && entry.threshold <= 0;
                boolean completed = rowCompleted == true || autoCompleted == true;
                if (completed == true && entry.effectType != null && entry.effectType.isEmpty() == false)
                    effectTypes.add(entry.effectType);
                previousCompleted = completed;
            }
            result.put(hullArchetype, effectTypes);
        }
        return result;
    }

    // 실드/요격체 사용 가능 여부 — 완료된 단계 효과에 해당 해금 효과가 있어야 함(슬롯이 있는 함체인지는 호출 측이 판단)
    public static boolean isModuleUnlocked(Map<String, Set<String>> completedEffectsByArchetype, String hullArchetype, EModuleType moduleType) {
        Set<String> effects = completedEffectsByArchetype.getOrDefault(hullArchetype, Set.of());
        if (effects.contains(k_effectUnlockShieldInterceptor) == true) return true;
        if (moduleType == EModuleType.shield) return effects.contains(k_effectUnlockShield);
        if (moduleType == EModuleType.interceptor) return effects.contains(k_effectUnlockInterceptor);
        return true;
    }

    // 카운터 이벤트 1건을 반영 — 이 아키타입의 "완료되지 않은 가장 낮은 단계"가 이 counterType을 세는 단계일 때만 delta를 더함
    // 반환: 이번 이벤트로 해금 효과(unlock_*)가 있는 단계가 완료됐으면 true — 호출 측이 갱신된 함대 정보를 내려줄지 판단
    @Transactional
    public boolean onCounterEvent(Long commanderId, String hullArchetype, String counterType, long delta) {
        if (delta <= 0) return false;

        List<GameDataService.HullArchetypePassiveEntry> passives = gameDataService.getHullArchetypePassives(hullArchetype);
        if (passives.isEmpty() == true) return false;

        Map<Integer, CommanderHullArchetypeProgress> rowByStage = loadOrCreateRows(commanderId, hullArchetype, passives);

        CommanderHullArchetypeProgress activeRow = null;
        GameDataService.HullArchetypePassiveEntry activeEntry = null;
        for (GameDataService.HullArchetypePassiveEntry entry : passives) {
            CommanderHullArchetypeProgress row = rowByStage.get(entry.stage);
            if (row.isCompleted() == true) continue;

            activeRow = row;
            activeEntry = entry;
            break;
        }
        if (activeRow == null) return false; // 전 단계 완료
        if (counterType.equals(activeEntry.counterType) == false) return false; // 지금 진행 중인 단계가 세는 이벤트가 아님

        long newValue = Math.min(activeRow.getCounterValue() + delta, (long) activeEntry.threshold);
        activeRow.setCounterValue(newValue);
        boolean unlockStageCompleted = false;
        if (newValue >= activeEntry.threshold) {
            activeRow.setCompleted(true);
            unlockStageCompleted = activeEntry.effectType != null && activeEntry.effectType.startsWith(k_effectUnlockPrefix) == true;
        }

        repository.saveAll(rowByStage.values());
        return unlockStageCompleted;
    }

    // 이 아키타입의 모든 단계 행을 확보 — 없는 행은 새로 만들고, 앞 단계가 완료됐을 때 문턱 0인 단계는 즉시 완료 처리(연쇄)
    private Map<Integer, CommanderHullArchetypeProgress> loadOrCreateRows(Long commanderId, String hullArchetype,
                                                                          List<GameDataService.HullArchetypePassiveEntry> passives) {
        Map<Integer, CommanderHullArchetypeProgress> rowByStage = new HashMap<>();
        for (CommanderHullArchetypeProgress row : repository.findByCommanderIdAndHullArchetype(commanderId, hullArchetype))
            rowByStage.put(row.getStage(), row);

        boolean previousCompleted = true;
        for (GameDataService.HullArchetypePassiveEntry entry : passives) {
            CommanderHullArchetypeProgress row = rowByStage.get(entry.stage);
            if (row == null) {
                row = new CommanderHullArchetypeProgress(commanderId, hullArchetype, entry.stage, entry.counterType);
                rowByStage.put(entry.stage, row);
            }
            if (row.isCompleted() == false && previousCompleted == true && entry.threshold <= 0)
                row.setCompleted(true);

            previousCompleted = row.isCompleted();
        }
        return rowByStage;
    }
}
