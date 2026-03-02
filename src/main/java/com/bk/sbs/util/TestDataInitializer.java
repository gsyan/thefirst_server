// 서버 시작 시 테스트용 더미 데이터 자동 생성 (JdbcTemplate 배치 INSERT)
// test.data.enabled / test.data.pvp.enabled / test.data.zone.enabled 로 개별 제어
package com.bk.sbs.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "test.data.enabled", havingValue = "true")
@Slf4j
public class TestDataInitializer {

    private static final int TEST_COUNT    = 100;
    private static final int BATCH_SIZE    = 500;

    // PVP 점수 범위
    private static final int SCORE_MAX     = 2000;
    private static final int SCORE_MIN     = 500;

    // Zone 목록 (1-1 ~ 9-10, 총 90개)
    private static final String[] ZONE_LIST = buildZoneList();

    private static String[] buildZoneList() {
        String[] zones = new String[90];
        int idx = 0;
        for (int ch = 1; ch <= 9; ch++)
            for (int st = 1; st <= 10; st++)
                zones[idx++] = ch + "-" + st;
        return zones;
    }

    @Value("${test.data.pvp.enabled:false}")
    private boolean pvpEnabled;

    @Value("${test.data.zone.enabled:false}")
    private boolean zoneEnabled;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public TestDataInitializer(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void initTestData() {
        List<Long> charIds = ensureBaseData();
        if (charIds.isEmpty()) return;

        if (pvpEnabled)  ensurePvpData(charIds);
        if (zoneEnabled) ensureZoneData(charIds);
    }

    // ── 기본 데이터 (account / character / fleet / ship / module_research) ──────

    private List<Long> ensureBaseData() {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account WHERE email = ?", Integer.class, buildEmail(1));
        if (exists != null && exists > 0) {
            log.info("TestDataInitializer: 기본 더미 데이터 이미 존재");
            return jdbc.queryForList(
                    "SELECT id FROM `character` WHERE character_name LIKE 'guest%' ORDER BY id",
                    Long.class);
        }

        log.info("TestDataInitializer: 기본 더미 데이터 {}개 생성 시작", TEST_COUNT);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String encodedPw = passwordEncoder.encode("testpassword1");

        // 1. Account
        List<Object[]> accountRows = new ArrayList<>(TEST_COUNT);
        for (int i = 1; i <= TEST_COUNT; i++)
            accountRows.add(new Object[]{buildEmail(i), encodedPw, false, now});
        jdbc.batchUpdate(
                "INSERT INTO account (email, password, deleted, date_time) VALUES (?, ?, ?, ?)",
                accountRows, BATCH_SIZE, (ps, row) -> {
                    ps.setString(1,  (String)    row[0]);
                    ps.setString(2,  (String)    row[1]);
                    ps.setBoolean(3, (boolean)   row[2]);
                    ps.setTimestamp(4, (Timestamp) row[3]);
                });

        // 2. Account ID 목록
        List<Long> accountIds = jdbc.queryForList(
                "SELECT id FROM account WHERE email LIKE 'guest\\_%@test.com' ORDER BY id",
                Long.class);

        // 3. Character
        List<Object[]> charRows = new ArrayList<>(TEST_COUNT);
        for (int i = 0; i < TEST_COUNT; i++)
            charRows.add(new Object[]{accountIds.get(i), buildCharName(i + 1), 5100L, now});
        jdbc.batchUpdate(
                "INSERT INTO `character` (account_id, character_name, tech_level, mineral," +
                " mineral_rare, mineral_exotic, mineral_dark," +
                " mineral_fraction, mineral_rare_fraction, mineral_exotic_fraction, mineral_dark_fraction," +
                " cleared_zone, deleted, date_time) VALUES (?, ?, 1, ?, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, '', false, ?)",
                charRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,  (Long)      row[0]);
                    ps.setString(2, (String)   row[1]);
                    ps.setLong(3,  (Long)      row[2]);
                    ps.setTimestamp(4, (Timestamp) row[3]);
                });

        // 4. Character ID 목록
        List<Long> charIds = jdbc.queryForList(
                "SELECT id FROM `character` WHERE character_name LIKE 'guest%' ORDER BY id",
                Long.class);

        // 5. Fleet
        List<Object[]> fleetRows = new ArrayList<>(TEST_COUNT);
        for (Long charId : charIds)
            fleetRows.add(new Object[]{charId, now});
        jdbc.batchUpdate(
                "INSERT INTO fleet (character_id, fleet_name, description, is_active, deleted," +
                " formation, created, modified) VALUES (?, 'Default Fleet', 'Auto-generated default fleet.'," +
                " true, false, 'formation_type_linear_horizontal', ?, ?)",
                fleetRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,  (Long)      row[0]);
                    ps.setTimestamp(2, (Timestamp) row[1]);
                    ps.setTimestamp(3, (Timestamp) row[1]);
                });

        // 6. Fleet ID 목록
        List<Long> fleetIds = jdbc.queryForList(
                "SELECT f.id FROM fleet f JOIN `character` c ON c.id = f.character_id" +
                " WHERE c.character_name LIKE 'guest%' ORDER BY f.id",
                Long.class);

        // 7. Ship
        List<Object[]> shipRows = new ArrayList<>(TEST_COUNT);
        for (Long fleetId : fleetIds)
            shipRows.add(new Object[]{fleetId, now});
        jdbc.batchUpdate(
                "INSERT INTO ship (fleet_id, ship_name, position_index," +
                " description, deleted, created, modified)" +
                " VALUES (?, 'Ship_1', 0, 'Auto-generated default ship.', false, ?, ?)",
                shipRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,  (Long)      row[0]);
                    ps.setTimestamp(2, (Timestamp) row[1]);
                    ps.setTimestamp(3, (Timestamp) row[1]);
                });

        // 8. Ship ID 목록
        List<Long> shipIds = jdbc.queryForList(
                "SELECT s.id FROM ship s JOIN fleet f ON f.id = s.fleet_id" +
                " JOIN `character` c ON c.id = f.character_id" +
                " WHERE c.character_name LIKE 'guest%' ORDER BY s.id",
                Long.class);

        // 9. ShipModule (body + engine)
        List<Object[]> moduleRows = new ArrayList<>(TEST_COUNT * 2);
        for (Long shipId : shipIds) {
            moduleRows.add(new Object[]{shipId, "body",   "body_t1_std",   1, now});
            moduleRows.add(new Object[]{shipId, "engine", "engine_t1_std", 1, now});
        }
        jdbc.batchUpdate(
                "INSERT INTO ship_module (ship_id, module_type, module_sub_type, module_level," +
                " body_index, slot_index, deleted, created, modified) VALUES (?, ?, ?, ?, 0, 0, false, ?, ?)",
                moduleRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,   (Long)      row[0]);
                    ps.setString(2, (String)    row[1]);
                    ps.setString(3, (String)    row[2]);
                    ps.setInt(4,    (int)        row[3]);
                    ps.setTimestamp(5, (Timestamp) row[4]);
                    ps.setTimestamp(6, (Timestamp) row[4]);
                });

        // 10. ModuleResearch
        String[][] researches = {
                {"body", "body_t1_std"}, {"engine", "engine_t1_std"},
                {"beam", "beam_t1_std"}, {"missile", "missile_t1_std"}, {"hanger", "hanger_t1_std"},
        };
        List<Object[]> researchRows = new ArrayList<>(TEST_COUNT * researches.length);
        for (Long charId : charIds)
            for (String[] r : researches)
                researchRows.add(new Object[]{charId, r[0], r[1], now});
        jdbc.batchUpdate(
                "INSERT INTO module_research (character_id, module_type, module_sub_type," +
                " researched, created, modified) VALUES (?, ?, ?, true, ?, ?)",
                researchRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,   (Long)      row[0]);
                    ps.setString(2, (String)    row[1]);
                    ps.setString(3, (String)    row[2]);
                    ps.setTimestamp(4, (Timestamp) row[3]);
                    ps.setTimestamp(5, (Timestamp) row[3]);
                });

        log.info("TestDataInitializer: 기본 더미 데이터 {}개 생성 완료", TEST_COUNT);
        return charIds;
    }

    // ── PVP 점수 데이터 ────────────────────────────────────────────────────────

    private void ensurePvpData(List<Long> charIds) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pvp_record WHERE character_id = ?",
                Integer.class, charIds.get(0));
        if (exists != null && exists > 0) {
            log.info("TestDataInitializer: PVP 더미 데이터 이미 존재, 스킵");
            return;
        }

        int total = charIds.size();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> pvpRows = new ArrayList<>(total);
        for (int i = 0; i < total; i++)
            pvpRows.add(new Object[]{charIds.get(i), calcPvpScore(i, total), now});
        jdbc.batchUpdate(
                "INSERT INTO pvp_record (character_id, score, wins, losses, last_updated)" +
                " VALUES (?, ?, 0, 0, ?)",
                pvpRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,  (Long)      row[0]);
                    ps.setInt(2,   (int)        row[1]);
                    ps.setTimestamp(3, (Timestamp) row[2]);
                });
        log.info("TestDataInitializer: PVP 더미 데이터 {}개 생성 완료 (점수 {}~{})",
                total, SCORE_MAX, SCORE_MIN);
    }

    // ── Zone 클리어 데이터 ─────────────────────────────────────────────────────

    private void ensureZoneData(List<Long> charIds) {
        String firstZone = jdbc.queryForObject(
                "SELECT cleared_zone FROM `character` WHERE id = ?",
                String.class, charIds.get(0));
        if (firstZone != null && firstZone.isEmpty() == false) {
            log.info("TestDataInitializer: Zone 더미 데이터 이미 존재, 스킵");
            return;
        }

        int total = charIds.size();
        List<Object[]> zoneRows = new ArrayList<>(total);
        for (int i = 0; i < total; i++)
            zoneRows.add(new Object[]{calcZoneName(i, total), charIds.get(i)});
        jdbc.batchUpdate(
                "UPDATE `character` SET cleared_zone = ? WHERE id = ?",
                zoneRows, BATCH_SIZE, (ps, row) -> {
                    ps.setString(1, (String) row[0]);
                    ps.setLong(2,   (Long)   row[1]);
                });
        log.info("TestDataInitializer: Zone 더미 데이터 {}개 생성 완료 ({}~{})",
                total, calcZoneName(0, total), calcZoneName(total - 1, total));
    }

    // ── 계산 헬퍼 ──────────────────────────────────────────────────────────────

    // i=0 → 2000점, i=total-1 → 500점 (균등 분포)
    private int calcPvpScore(int i, int total) {
        return (int) Math.round(SCORE_MAX - (double)(SCORE_MAX - SCORE_MIN) * i / (total - 1));
    }

    // i=0 → "9-10"(최고), i=total-1 → "1-1"(최저) (유효 존 목록 기준 균등 분포)
    private String calcZoneName(int i, int total) {
        int zoneIndex = (int) Math.round((double)(ZONE_LIST.length - 1) * (total - 1 - i) / (total - 1));
        return ZONE_LIST[zoneIndex];
    }

    private String buildEmail(int seq)    { return "guest_" + String.format("%04d", seq) + "@test.com"; }
    private String buildCharName(int seq) { return "guest"  + String.format("%04d", seq); }
}
