// 서버 시작 시 PvP 랭킹 테스트용 더미 데이터 2000개 자동 생성 (JdbcTemplate 배치 INSERT)
// @Order(1) 으로 syncRedisFromDb(@Order(2)) 보다 먼저 실행, 이미 데이터 있으면 스킵
package com.bk.sbs.util;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class TestDataInitializer {

    private static final int TEST_COUNT = 2000;
    private static final int SCORE_MAX  = 2000;
    private static final int SCORE_MIN  = 500;
    private static final int BATCH_SIZE = 500;

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
        String firstEmail = buildEmail(1);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account WHERE email = ?", Integer.class, firstEmail);
        if (exists != null && exists > 0) {
            log.info("TestDataInitializer: 더미 데이터 이미 존재, 스킵");
            return;
        }

        log.info("TestDataInitializer: 더미 계정 {}개 배치 생성 시작", TEST_COUNT);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String encodedPassword = passwordEncoder.encode("testpassword1");

        // 1. Account 배치 INSERT
        List<Object[]> accountRows = new ArrayList<>(TEST_COUNT);
        for (int i = 1; i <= TEST_COUNT; i++) {
            accountRows.add(new Object[]{buildEmail(i), encodedPassword, false, now});
        }
        jdbc.batchUpdate(
                "INSERT INTO account (email, password, deleted, date_time) VALUES (?, ?, ?, ?)",
                accountRows, BATCH_SIZE, (ps, row) -> {
                    ps.setString(1, (String) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setBoolean(3, (boolean) row[2]);
                    ps.setTimestamp(4, (Timestamp) row[3]);
                });

        // 2. Account ID 목록 조회 (생성 순서 보장)
        List<Long> accountIds = jdbc.queryForList(
                "SELECT id FROM account WHERE email LIKE 'guest\\_%@test.com' ORDER BY id",
                Long.class);

        // 3. Character 배치 INSERT
        List<Object[]> charRows = new ArrayList<>(TEST_COUNT);
        for (int i = 0; i < TEST_COUNT; i++) {
            charRows.add(new Object[]{accountIds.get(i), buildCharName(i + 1), 5100L, now});
        }
        jdbc.batchUpdate(
                "INSERT INTO `character` (account_id, character_name, tech_level, mineral," +
                " mineral_rare, mineral_exotic, mineral_dark," +
                " mineral_fraction, mineral_rare_fraction, mineral_exotic_fraction, mineral_dark_fraction," +
                " cleared_zone, deleted, date_time) VALUES (?, ?, 1, ?, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, '', false, ?)",
                charRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1, (Long) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setLong(3, (Long) row[2]);
                    ps.setTimestamp(4, (Timestamp) row[3]);
                });

        // 4. Character ID 목록 조회
        List<Long> charIds = jdbc.queryForList(
                "SELECT id FROM `character` WHERE character_name LIKE 'guest%' ORDER BY id",
                Long.class);

        // 5. Fleet 배치 INSERT (is_active=true)
        List<Object[]> fleetRows = new ArrayList<>(TEST_COUNT);
        for (int i = 0; i < TEST_COUNT; i++) {
            fleetRows.add(new Object[]{charIds.get(i), now});
        }
        jdbc.batchUpdate(
                "INSERT INTO fleet (character_id, fleet_name, description, is_active, deleted," +
                " formation, created, modified) VALUES (?, 'Default Fleet', 'Auto-generated default fleet.'," +
                " true, false, 'formation_type_linear_horizontal', ?, ?)",
                fleetRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1, (Long) row[0]);
                    ps.setTimestamp(2, (Timestamp) row[1]);
                    ps.setTimestamp(3, (Timestamp) row[1]);
                });

        // 6. Fleet ID 목록 조회
        List<Long> fleetIds = jdbc.queryForList(
                "SELECT f.id FROM fleet f" +
                " JOIN `character` c ON c.id = f.character_id" +
                " WHERE c.character_name LIKE 'guest%' ORDER BY f.id",
                Long.class);

        // 7. Ship 배치 INSERT (함대당 1척)
        List<Object[]> shipRows = new ArrayList<>(TEST_COUNT);
        for (int i = 0; i < TEST_COUNT; i++) {
            shipRows.add(new Object[]{fleetIds.get(i), now});
        }
        jdbc.batchUpdate(
                "INSERT INTO ship (fleet_id, ship_name, position_index," +
                " description, deleted, created, modified)" +
                " VALUES (?, 'Ship_1', 0, 'Auto-generated default ship.', false, ?, ?)",
                shipRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1, (Long) row[0]);
                    ps.setTimestamp(2, (Timestamp) row[1]);
                    ps.setTimestamp(3, (Timestamp) row[1]);
                });

        // 8. Ship ID 목록 조회
        List<Long> shipIds = jdbc.queryForList(
                "SELECT s.id FROM ship s" +
                " JOIN fleet f ON f.id = s.fleet_id" +
                " JOIN `character` c ON c.id = f.character_id" +
                " WHERE c.character_name LIKE 'guest%' ORDER BY s.id",
                Long.class);

        // 9. ShipModule 배치 INSERT (body, engine 각 1개씩)
        List<Object[]> moduleRows = new ArrayList<>(TEST_COUNT * 2);
        for (Long shipId : shipIds) {
            moduleRows.add(new Object[]{shipId, "body",   "body_t1_std",   1, now});
            moduleRows.add(new Object[]{shipId, "engine", "engine_t1_std", 1, now});
        }
        jdbc.batchUpdate(
                "INSERT INTO ship_module (ship_id, module_type, module_sub_type, module_level," +
                " body_index, slot_index, deleted, created, modified) VALUES (?, ?, ?, ?, 0, 0, false, ?, ?)",
                moduleRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1, (Long) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setInt(4, (int) row[3]);
                    ps.setTimestamp(5, (Timestamp) row[4]);
                    ps.setTimestamp(6, (Timestamp) row[4]);
                });

        // 10. ModuleResearch 배치 INSERT (캐릭터당 5종)
        String[][] researches = {
                {"body",    "body_t1_std"},
                {"engine",  "engine_t1_std"},
                {"beam",    "beam_t1_std"},
                {"missile", "missile_t1_std"},
                {"hanger",  "hanger_t1_std"},
        };
        List<Object[]> researchRows = new ArrayList<>(TEST_COUNT * researches.length);
        for (Long charId : charIds) {
            for (String[] r : researches) {
                researchRows.add(new Object[]{charId, r[0], r[1], now});
            }
        }
        jdbc.batchUpdate(
                "INSERT INTO module_research (character_id, module_type, module_sub_type," +
                " researched, created, modified) VALUES (?, ?, ?, true, ?, ?)",
                researchRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1, (Long) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setTimestamp(4, (Timestamp) row[3]);
                    ps.setTimestamp(5, (Timestamp) row[3]);
                });

        // 11. PvpRecord 배치 INSERT (점수 2000~500 균등 분포)
        List<Object[]> pvpRows = new ArrayList<>(TEST_COUNT);
        for (int i = 0; i < TEST_COUNT; i++) {
            pvpRows.add(new Object[]{charIds.get(i), calcScore(i), now});
        }
        jdbc.batchUpdate(
                "INSERT INTO pvp_record (character_id, score, wins, losses, last_updated)" +
                " VALUES (?, ?, 0, 0, ?)",
                pvpRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1, (Long) row[0]);
                    ps.setInt(2, (int) row[1]);
                    ps.setTimestamp(3, (Timestamp) row[2]);
                });

        log.info("TestDataInitializer: 더미 계정 {}개 생성 완료 (점수 {}~{})", TEST_COUNT, SCORE_MAX, SCORE_MIN);
    }

    // i=0 → 2000점, i=1999 → 500점 (균등 분포)
    private int calcScore(int i) {
        return (int) Math.round(SCORE_MAX - (double) (SCORE_MAX - SCORE_MIN) * i / (TEST_COUNT - 1));
    }

    private String buildEmail(int seq) {
        return "guest_" + String.format("%04d", seq) + "@test.com";
    }

    private String buildCharName(int seq) {
        return "guest" + String.format("%04d", seq);
    }
}
