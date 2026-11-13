package com.bk.sbs.service;

import com.bk.sbs.entity.CommanderHullArchetypeProgress;
import com.bk.sbs.repository.CommanderHullArchetypeProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 패시브 단계 진행 규칙 검증 — 엄격한 순차, 초과분 버림, 진행 중이 아닌 카운터 무시
// centurion: 1단계(0, destroy) → 2단계(10, destroy) → 3단계(20, destroy) → 4단계(10, zone_run_clear)
class HullArchetypeProgressServiceTest {

    private static final long k_commanderId = 1L;
    private static final String k_archetype = "centurion";
    private static final String k_destroy = "destroy_enemy_ship";
    private static final String k_zoneClear = "zone_run_clear";

    private List<CommanderHullArchetypeProgress> store;
    private HullArchetypeProgressService service;

    @BeforeEach
    void setUp() {
        store = new ArrayList<>();

        CommanderHullArchetypeProgressRepository repository = mock(CommanderHullArchetypeProgressRepository.class);
        when(repository.findByCommanderIdAndHullArchetype(anyLong(), anyString())).thenAnswer(inv -> new ArrayList<>(store));
        when(repository.saveAll(any(Collection.class))).thenAnswer(inv -> {
            Collection<CommanderHullArchetypeProgress> rows = inv.getArgument(0);
            for (CommanderHullArchetypeProgress row : rows) {
                if (store.contains(row) == false) store.add(row);
            }
            return new ArrayList<>(rows);
        });

        GameDataService gameDataService = mock(GameDataService.class);
        List<GameDataService.HullArchetypePassiveEntry> passives = List.of(
                new GameDataService.HullArchetypePassiveEntry(k_archetype, 1, k_destroy, 0),
                new GameDataService.HullArchetypePassiveEntry(k_archetype, 2, k_destroy, 10),
                new GameDataService.HullArchetypePassiveEntry(k_archetype, 3, k_destroy, 20),
                new GameDataService.HullArchetypePassiveEntry(k_archetype, 4, k_zoneClear, 10));
        when(gameDataService.getHullArchetypePassives(k_archetype)).thenReturn(passives);
        when(gameDataService.getHullArchetypePassives("unknown")).thenReturn(List.of());

        service = new HullArchetypeProgressService(repository, gameDataService);
    }

    private CommanderHullArchetypeProgress row(int stage) {
        return store.stream().filter(r -> r.getStage() == stage).findFirst().orElseThrow();
    }

    @Test
    void stageOneIsCompletedImmediatelyAndStageTwoStartsAtZero() {
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 3);

        assertTrue(row(1).isCompleted());
        assertFalse(row(2).isCompleted());
        assertEquals(3, row(2).getCounterValue());
        assertEquals(0, row(3).getCounterValue());
    }

    @Test
    void overflowIsDiscardedAndNextStageStartsFromZero() {
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 15); // 문턱 10 → 5는 버림

        assertTrue(row(2).isCompleted());
        assertEquals(10, row(2).getCounterValue());
        assertFalse(row(3).isCompleted());
        assertEquals(0, row(3).getCounterValue());

        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 7);
        assertEquals(7, row(3).getCounterValue()); // 3단계는 0부터 센 값
    }

    @Test
    void laterStageDoesNotCountUntilPreviousStageCompletes() {
        service.onCounterEvent(k_commanderId, k_archetype, k_zoneClear, 5); // 2단계 진행 중 — 존런 이벤트는 버려지고 저장도 안 함

        assertTrue(store.isEmpty());

        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 2); // 이후 격침은 2단계에만 쌓임
        assertEquals(2, row(2).getCounterValue());
        assertEquals(0, row(4).getCounterValue());
    }

    @Test
    void zoneRunClearCountsOnlyAfterStageThreeCompletes() {
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 10);
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 20);
        assertTrue(row(3).isCompleted());

        service.onCounterEvent(k_commanderId, k_archetype, k_zoneClear, 4);
        assertEquals(4, row(4).getCounterValue());
        assertFalse(row(4).isCompleted());

        service.onCounterEvent(k_commanderId, k_archetype, k_zoneClear, 6);
        assertTrue(row(4).isCompleted());
    }

    @Test
    void completedStagesAreNotCountedAgain() {
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 10);
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 20);
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 100); // 3단계 완료 뒤 destroy는 4단계(zone)가 세지 않아 버려짐

        assertEquals(10, row(2).getCounterValue());
        assertEquals(20, row(3).getCounterValue());
        assertEquals(0, row(4).getCounterValue());
    }

    @Test
    void unlockEffectIsReportedOnlyWhenItsStageCompletes() {
        GameDataService.HullArchetypePassiveEntry stage1 = new GameDataService.HullArchetypePassiveEntry("colossus", 1, k_destroy, 0);
        GameDataService.HullArchetypePassiveEntry stage2 = new GameDataService.HullArchetypePassiveEntry("colossus", 2, k_destroy, 5);
        stage2.effectType = "unlock_interceptor";
        GameDataService.HullArchetypePassiveEntry stage3 = new GameDataService.HullArchetypePassiveEntry("colossus", 3, k_destroy, 5);
        stage3.effectType = "unlock_shield";
        GameDataService.HullArchetypePassiveEntry aegis1 = new GameDataService.HullArchetypePassiveEntry("aegis", 1, k_destroy, 0);
        aegis1.effectType = "unlock_shield_interceptor";

        CommanderHullArchetypeProgressRepository repository = mock(CommanderHullArchetypeProgressRepository.class);
        when(repository.findByCommanderIdAndHullArchetype(anyLong(), anyString())).thenAnswer(inv -> new ArrayList<>(store));
        when(repository.findByCommanderId(anyLong())).thenAnswer(inv -> new ArrayList<>(store));
        when(repository.saveAll(any(Collection.class))).thenAnswer(inv -> {
            Collection<CommanderHullArchetypeProgress> rows = inv.getArgument(0);
            for (CommanderHullArchetypeProgress row : rows) {
                if (store.contains(row) == false) store.add(row);
            }
            return new ArrayList<>(rows);
        });
        GameDataService gameDataService = mock(GameDataService.class);
        when(gameDataService.getHullArchetypePassives("colossus")).thenReturn(List.of(stage1, stage2, stage3));
        when(gameDataService.getHullArchetypePassives("aegis")).thenReturn(List.of(aegis1));
        when(gameDataService.getHullArchetypesWithPassives()).thenReturn(java.util.Set.of("colossus", "aegis"));
        HullArchetypeProgressService unlockService = new HullArchetypeProgressService(repository, gameDataService);

        // 진행 전: aegis는 1단계(문턱 0)라 행이 없어도 해금, colossus는 둘 다 잠김
        java.util.Map<String, java.util.Set<String>> before = unlockService.getCompletedEffectTypesByArchetype(k_commanderId);
        assertTrue(HullArchetypeProgressService.isModuleUnlocked(before, "aegis", com.bk.sbs.enums.EModuleType.shield));
        assertTrue(HullArchetypeProgressService.isModuleUnlocked(before, "aegis", com.bk.sbs.enums.EModuleType.interceptor));
        assertFalse(HullArchetypeProgressService.isModuleUnlocked(before, "colossus", com.bk.sbs.enums.EModuleType.interceptor));
        assertFalse(HullArchetypeProgressService.isModuleUnlocked(before, "colossus", com.bk.sbs.enums.EModuleType.shield));

        assertFalse(unlockService.onCounterEvent(k_commanderId, "colossus", k_destroy, 3)); // 2단계 미완료
        assertTrue(unlockService.onCounterEvent(k_commanderId, "colossus", k_destroy, 2));  // 2단계 완료 → 요격체 해금

        java.util.Map<String, java.util.Set<String>> after = unlockService.getCompletedEffectTypesByArchetype(k_commanderId);
        assertTrue(HullArchetypeProgressService.isModuleUnlocked(after, "colossus", com.bk.sbs.enums.EModuleType.interceptor));
        assertFalse(HullArchetypeProgressService.isModuleUnlocked(after, "colossus", com.bk.sbs.enums.EModuleType.shield));
    }

    @Test
    void unknownArchetypeOrNonPositiveDeltaDoesNothing() {
        service.onCounterEvent(k_commanderId, "unknown", k_destroy, 5);
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, 0);
        service.onCounterEvent(k_commanderId, k_archetype, k_destroy, -3);

        assertTrue(store.isEmpty());
    }
}
