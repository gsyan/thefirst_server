// 테스트 계정(guest_test0001~)에 서로 다른 빌드(이름/레벨/함선 배치/패시브 단계/모듈강화/전적)를 덮어씀 — PVP 상대 정보·스폰 검증용
// test.data.profile.enabled=true일 때만 서버 기동마다 실행(dev 전용). 계정마다 트랜잭션을 분리해 한 계정이 실패해도 나머지는 적용됨
package com.bk.sbs.util;

import com.bk.sbs.dto.FleetPlaceShipRequest;
import com.bk.sbs.dto.FleetRemoveShipRequest;
import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.service.FleetService;
import com.bk.sbs.service.GameDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class TestAccountProfileApplier {

    // 함선 1척 배치 — hullSubType은 1레벨 원본 서브타입(강화 레벨은 UpgradeDef로 따로 지정)
    private record ShipDef(String hullSubType, boolean isFront) {}

    // 아키타입 패시브를 몇 단계까지 완료한 상태로 둘지
    private record PassiveDef(String hullArchetype, int completedStage) {}

    // 모듈강화 — effectLevel은 실제 효과 레벨(1=강화 없음), DB에는 구매 횟수(effectLevel-1)로 저장
    private record UpgradeDef(String hullArchetype, EModuleType moduleType, int slotIndex, int effectLevel) {}

    private record Profile(String email, String commanderName, int commanderLevel, int wins, int losses,
                           List<ShipDef> ships, List<PassiveDef> passives, List<UpgradeDef> upgrades) {}

    private static final List<Profile> PROFILES = List.of(
            // A: 자기 함선(self) 패시브 + 강화 레벨 확인
            new Profile("guest_test0001", "ScarletLancer", 9, 0, 0,
                    List.of(new ShipDef("hull_1_centurion_1", true),
                            new ShipDef("hull_2_prism_1", false)),
                    List.of(new PassiveDef("centurion", 3),
                            new PassiveDef("prism", 2)),
                    List.of(new UpgradeDef("centurion", EModuleType.hull, 0, 3),
                            new UpgradeDef("centurion", EModuleType.beam, 0, 3),
                            new UpgradeDef("prism", EModuleType.beam, 0, 2))),
            // B: 실드·요격체 해금 노출 + 함대(fleet) 범위 효과 확인
            new Profile("guest_test0002", "TitanBulwark", 21, 12, 8,
                    List.of(new ShipDef("hull_3_colossus_1", true),
                            new ShipDef("hull_3_leviathan_1", false)),
                    List.of(new PassiveDef("colossus", 3),
                            new PassiveDef("leviathan", 2)),
                    List.of(new UpgradeDef("colossus", EModuleType.hull, 0, 5),
                            new UpgradeDef("colossus", EModuleType.shield, 0, 3))),
            // C: 높은 강화 + 함대 효과 중첩 + 슬롯별 레벨 구분 확인
            new Profile("guest_test0003", "AstraSovereign", 35, 40, 3,
                    List.of(new ShipDef("hull_5_aegis_1", true),
                            new ShipDef("hull_5_phoenix_1", true),
                            new ShipDef("hull_4_talon_1", false)),
                    List.of(new PassiveDef("aegis", 2),
                            new PassiveDef("phoenix", 4),
                            new PassiveDef("talon", 4)),
                    List.of(new UpgradeDef("aegis", EModuleType.hull, 0, 10),
                            new UpgradeDef("aegis", EModuleType.beam, 0, 5),
                            new UpgradeDef("aegis", EModuleType.beam, 1, 1),
                            new UpgradeDef("phoenix", EModuleType.hull, 0, 10),
                            new UpgradeDef("talon", EModuleType.hull, 0, 7),
                            new UpgradeDef("talon", EModuleType.beam, 0, 4),
                            new UpgradeDef("talon", EModuleType.missile, 0, 6),
                            new UpgradeDef("talon", EModuleType.hangar, 0, 2)))
    );

    @Value("${test.data.profile.enabled:false}")
    private boolean profileEnabled;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final FleetService fleetService;
    private final GameDataService gameDataService;

    public TestAccountProfileApplier(JdbcTemplate jdbc, TransactionTemplate transactionTemplate,
                                     FleetService fleetService, GameDataService gameDataService) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.fleetService = fleetService;
        this.gameDataService = gameDataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void applyTestProfiles() {
        if (profileEnabled == false) return;

        for (Profile profile : PROFILES) {
            try {
                transactionTemplate.executeWithoutResult(status -> applyProfile(profile));
            } catch (Exception e) {
                log.error("TestAccountProfileApplier: {} 적용 실패 — {}", profile.email(), e.getMessage(), e);
            }
        }
    }

    private void applyProfile(Profile profile) {
        List<Long> found = jdbc.queryForList(
                "SELECT c.id FROM commander c JOIN account a ON a.id = c.account_id WHERE a.email = ?",
                Long.class, profile.email());
        if (found.isEmpty() == true) {
            log.warn("TestAccountProfileApplier: {} 계정 없음, 스킵", profile.email());
            return;
        }
        Long commanderId = found.get(0);

        applyName(commanderId, profile);
        jdbc.update("UPDATE commander SET commander_level = ?, exp = 0 WHERE id = ?", profile.commanderLevel(), commanderId);
        applyUnlockedHulls(commanderId, profile);
        int passiveRowCount = applyPassives(commanderId, profile);
        int upgradeRowCount = applyUpgrades(commanderId, profile);
        applyFleet(commanderId, profile);
        jdbc.update("UPDATE pvp_record SET wins = ?, losses = ? WHERE commander_id = ?", profile.wins(), profile.losses(), commanderId);

        log.info("TestAccountProfileApplier: {}(id={}) 적용 — name={}, level={}, ships={}, passiveRows={}, upgradeRows={}, record={}W{}L",
                profile.email(), commanderId, profile.commanderName(), profile.commanderLevel(), profile.ships().size(),
                passiveRowCount, upgradeRowCount, profile.wins(), profile.losses());
    }

    // 다른 지휘관이 이미 쓰는 이름이면 이름만 건너뜀(commander_name UNIQUE)
    private void applyName(Long commanderId, Profile profile) {
        Integer usedByOther = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commander WHERE commander_name = ? AND id <> ?",
                Integer.class, profile.commanderName(), commanderId);
        if (usedByOther != null && usedByOther > 0) {
            log.warn("TestAccountProfileApplier: 이름 '{}'은 다른 지휘관이 사용 중 — {} 이름 변경 스킵", profile.commanderName(), profile.email());
            return;
        }
        jdbc.update("UPDATE commander SET commander_name = ? WHERE id = ?", profile.commanderName(), commanderId);
    }

    // 티어2+ 함체는 해금 기록이 있어야 배치 가능(placeFleetShip 검사) — 없을 때만 추가
    private void applyUnlockedHulls(Long commanderId, Profile profile) {
        for (ShipDef ship : profile.ships()) {
            int tier = GameDataService.parseTierFromHullSubType(ship.hullSubType());
            if (tier <= 1) continue;

            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commander_unlocked_hull WHERE commander_id = ? AND hull_sub_type = ?",
                    Integer.class, commanderId, ship.hullSubType());
            if (exists != null && exists > 0) continue;

            jdbc.update("INSERT INTO commander_unlocked_hull (commander_id, hull_sub_type, unlocked_at) VALUES (?, ?, ?)",
                    commanderId, ship.hullSubType(), Timestamp.from(Instant.now()));
        }
    }

    // 진행도 전부 지우고 완료 단계까지 completed 행(counter_value=문턱)을 넣음 — 다음 단계 행은 첫 카운터 이벤트 때 서비스가 생성
    private int applyPassives(Long commanderId, Profile profile) {
        jdbc.update("DELETE FROM commander_hull_archetype_progress WHERE commander_id = ?", commanderId);

        int insertedRows = 0;
        for (PassiveDef passive : profile.passives()) {
            for (GameDataService.HullArchetypePassiveEntry entry : gameDataService.getHullArchetypePassives(passive.hullArchetype())) {
                if (entry.stage > passive.completedStage()) continue;

                jdbc.update("INSERT INTO commander_hull_archetype_progress (commander_id, hull_archetype, stage, counter_type, counter_value, completed)" +
                                " VALUES (?, ?, ?, ?, ?, 1)",
                        commanderId, passive.hullArchetype(), entry.stage, entry.counterType, (long) entry.threshold);
                insertedRows++;
            }
        }
        return insertedRows;
    }

    // 강화 전부 지우고 구성대로 넣음 — 그 함체에 없는 무기 슬롯은 경고 후 스킵
    private int applyUpgrades(Long commanderId, Profile profile) {
        jdbc.update("DELETE FROM commander_archetype_module_upgrade WHERE commander_id = ?", commanderId);

        int insertedRows = 0;
        for (UpgradeDef upgrade : profile.upgrades()) {
            if (isUpgradeSlotValid(profile, upgrade) == false) {
                log.warn("TestAccountProfileApplier: {} {} {} 슬롯{} 없음 — 강화 스킵",
                        profile.email(), upgrade.hullArchetype(), upgrade.moduleType(), upgrade.slotIndex());
                continue;
            }
            int purchaseCount = upgrade.effectLevel() - 1;
            jdbc.update("INSERT INTO commander_archetype_module_upgrade (commander_id, hull_archetype, module_type, slot_index, level) VALUES (?, ?, ?, ?, ?)",
                    commanderId, upgrade.hullArchetype(), upgrade.moduleType().name(), upgrade.slotIndex(), purchaseCount);
            insertedRows++;
        }
        return insertedRows;
    }

    private boolean isUpgradeSlotValid(Profile profile, UpgradeDef upgrade) {
        int slotTypeIndex = getSlotTypeIndex(upgrade.moduleType());
        if (slotTypeIndex < 0) return upgrade.slotIndex() == 0; // hull: 슬롯 개념 없음

        for (ShipDef ship : profile.ships()) {
            ModuleData hullData = gameDataService.getHullModuleData(ship.hullSubType());
            if (hullData == null || upgrade.hullArchetype().equals(hullData.getHullArchetype()) == false) continue;

            int[] maxSlots = gameDataService.getMaxSlotsForHull(ship.hullSubType());
            return upgrade.slotIndex() < maxSlots[slotTypeIndex];
        }
        return false;
    }

    // getMaxSlotsForHull 배열 인덱스 [beam, missile, hangar, shield, interceptor] — hull은 -1
    private int getSlotTypeIndex(EModuleType moduleType) {
        if (moduleType == EModuleType.beam) return 0;
        if (moduleType == EModuleType.missile) return 1;
        if (moduleType == EModuleType.hangar) return 2;
        if (moduleType == EModuleType.shield) return 3;
        if (moduleType == EModuleType.interceptor) return 4;
        return -1;
    }

    // 기존 함선을 전부 해제한 뒤 실제 게임 경로(placeFleetShip)로 배치 — 기본 모듈 장착/해금/지휘력 검사가 그대로 적용됨
    private void applyFleet(Long commanderId, Profile profile) {
        List<Integer> currentSlots = jdbc.queryForList(
                "SELECT s.slot_index FROM ship s JOIN fleet f ON f.id = s.fleet_id WHERE f.commander_id = ? AND f.fleet_index = 0",
                Integer.class, commanderId);
        for (Integer slotIndex : currentSlots) {
            FleetRemoveShipRequest removeRequest = new FleetRemoveShipRequest();
            removeRequest.setSlotIndex(slotIndex);
            fleetService.removeFleetShip(commanderId, removeRequest);
        }

        for (int i = 0; i < profile.ships().size(); i++) {
            ShipDef ship = profile.ships().get(i);
            FleetPlaceShipRequest placeRequest = new FleetPlaceShipRequest();
            placeRequest.setSlotIndex(i);
            placeRequest.setHullSubType(ship.hullSubType());
            placeRequest.setIsFront(ship.isFront());
            fleetService.placeFleetShip(commanderId, placeRequest);
        }
    }
}
