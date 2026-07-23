// 서버 시작 시 테스트용 더미 데이터 자동 생성 (JdbcTemplate 배치 INSERT)
// test.data.count 로 생성 수 제어, pvp/zone base/deviation 으로 점수·존 분포 설정
package com.bk.sbs.util;

import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.service.GameDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private static final int BATCH_SIZE = 500;

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

    // 생성할 테스트 계정 수 (0 = 생성 안 함)
    @Value("${test.data.count:0}")
    private int requestedCount;

    @Value("${test.data.pvp.enabled:false}")
    private boolean pvpEnabled;

    // PVP 점수 중간값 (i=0 에 배정되는 점수)
    @Value("${test.data.pvp.base-score:1000}")
    private int pvpBaseScore;

    // PVP 점수 간격: base, base-dev, base+dev, base-2*dev, base+2*dev ...
    @Value("${test.data.pvp.deviation:100}")
    private int pvpDeviation;

    @Value("${test.data.zone.enabled:false}")
    private boolean zoneEnabled;

    // Zone 기준값 (예: "5-5"), i=0 에 배정
    @Value("${test.data.zone.base:5-5}")
    private String zoneBase;

    // Zone 인덱스 간격
    @Value("${test.data.zone.deviation:5}")
    private int zoneDeviation;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final GameDataService gameDataService;

    public TestDataInitializer(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, GameDataService gameDataService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.gameDataService = gameDataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void initTestData() {
        if (requestedCount <= 0) return;

        int effectiveCount = clampCount(requestedCount);
        if (effectiveCount != requestedCount)
            log.warn("TestDataInitializer: count={} 요청 → 설정 범위 초과로 {}로 자동 조정 (pvpEnabled={}, pvpBase={}, pvpDev={}, zoneBase={}, zoneDev={})",
                    requestedCount, effectiveCount, pvpEnabled, pvpBaseScore, pvpDeviation, zoneBase, zoneDeviation);

        List<Long> charIds = ensureBaseData(effectiveCount);
        if (charIds.isEmpty()) return;

        if (pvpEnabled)  ensurePvpData(charIds);
        if (zoneEnabled) ensureZoneData(charIds);
    }

    /**
     * 요청 count를 pvp/zone 분포 설정 한계로 클램프
     * pvp: base - k*dev >= 0 → maxK = base / dev → max = 2*maxK + 1
     * zone: 기준 인덱스 양쪽 여유 기준 동일 계산
     */
    private int clampCount(int requested) {
        int max = Integer.MAX_VALUE;
        if (pvpEnabled && pvpDeviation > 0) {
            int maxK   = pvpBaseScore / pvpDeviation;
            int maxPvp = 2 * maxK + 1;
            max = Math.min(max, maxPvp);
        }
        if (zoneEnabled && zoneDeviation > 0) {
            int baseIdx = parseZoneIndex(zoneBase);
            int maxK    = Math.min(baseIdx / zoneDeviation, (ZONE_LIST.length - 1 - baseIdx) / zoneDeviation);
            int maxZone = 2 * maxK + 1;
            max = Math.min(max, maxZone);
        }
        return max == Integer.MAX_VALUE ? requested : Math.min(requested, max);
    }

    // ── 기본 데이터 (account / commander / fleet / ship / module_research) ──────

    private List<Long> ensureBaseData(int count) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account WHERE email = ?", Integer.class, buildEmail(1));
        if (exists != null && exists > 0) {
            log.info("TestDataInitializer: 기본 더미 데이터 이미 존재");
            return jdbc.queryForList(
                    "SELECT c.id FROM commander c JOIN account a ON a.id = c.account_id" +
                    " WHERE a.email LIKE 'guest\\_test%' ORDER BY c.id",
                    Long.class);
        }

        Long charAutoInc = jdbc.queryForObject(
                "SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_NAME = 'commander' AND TABLE_SCHEMA = DATABASE()",
                Long.class);
        Long accAutoInc = jdbc.queryForObject(
                "SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_NAME = 'account' AND TABLE_SCHEMA = DATABASE()",
                Long.class);
        log.info("TestDataInitializer: 기본 더미 데이터 {}개 생성 시작 — account AUTO_INCREMENT={}, commander AUTO_INCREMENT={}",
                count, accAutoInc, charAutoInc);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String encodedPw = passwordEncoder.encode("testpassword1");

        // 1. Account
        List<Object[]> accountRows = new ArrayList<>(count);
        for (int i = 1; i <= count; i++)
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
                "SELECT id FROM account WHERE email LIKE 'guest\\_test%' ORDER BY id",
                Long.class);

        // 3. Commander
        List<Object[]> charRows = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            charRows.add(new Object[]{accountIds.get(i), "commander_" + (charAutoInc + i), 2, now});
        jdbc.batchUpdate(
                "INSERT INTO commander (account_id, commander_name, mineral, deleted, date_time)" +
                " VALUES (?, ?, ?, false, ?)",
                charRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,   (Long)      row[0]);
                    ps.setString(2, (String)    row[1]);
                    ps.setInt(3,    (Integer)   row[2]);
                    ps.setTimestamp(4, (Timestamp) row[3]);
                });

        // 4. Commander ID 목록
        List<Long> charIds = jdbc.queryForList(
                "SELECT c.id FROM commander c JOIN account a ON a.id = c.account_id" +
                " WHERE a.email LIKE 'guest\\_test%' ORDER BY c.id",
                Long.class);

        // 5. Fleet
        List<Object[]> fleetRows = new ArrayList<>(count);
        for (Long charId : charIds)
            fleetRows.add(new Object[]{charId, now});
        jdbc.batchUpdate(
                "INSERT INTO fleet (commander_id, fleet_name, description, is_active, deleted," +
                " formation, created, modified) VALUES (?, 'Default Fleet', 'Auto-generated default fleet.'," +
                " true, false, 'linear_horizontal', ?, ?)",
                fleetRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,  (Long)      row[0]);
                    ps.setTimestamp(2, (Timestamp) row[1]);
                    ps.setTimestamp(3, (Timestamp) row[1]);
                });

        // 6. Fleet ID 목록
        List<Long> fleetIds = jdbc.queryForList(
                "SELECT f.id FROM fleet f JOIN commander c ON c.id = f.commander_id" +
                " JOIN account a ON a.id = c.account_id WHERE a.email LIKE 'guest\\_test%' ORDER BY f.id",
                Long.class);

        // 7. Ship
        List<Object[]> shipRows = new ArrayList<>(count);
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
                " JOIN commander c ON c.id = f.commander_id" +
                " JOIN account a ON a.id = c.account_id WHERE a.email LIKE 'guest\\_test%' ORDER BY s.id",
                Long.class);

        // 9. ShipModule (body only)
        ModuleData bodyData = gameDataService.getFirstModuleByType(EModuleType.body);
        float bodyMaxHealth = (bodyData != null && bodyData.getHealth() != null) ? bodyData.getHealth() : 0f;
        List<Object[]> moduleRows = new ArrayList<>(count);
        for (Long shipId : shipIds) {
            moduleRows.add(new Object[]{shipId, "body", "body_t1_m111", 1, bodyMaxHealth, now});
        }
        jdbc.batchUpdate(
                "INSERT INTO ship_module (ship_id, module_type, module_sub_type, module_level," +
                " body_index, slot_index, current_health, deleted, created, modified) VALUES (?, ?, ?, ?, 0, 0, ?, false, ?, ?)",
                moduleRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,   (Long)      row[0]);
                    ps.setString(2, (String)    row[1]);
                    ps.setString(3, (String)    row[2]);
                    ps.setInt(4,    (int)        row[3]);
                    ps.setFloat(5,  (float)      row[4]);
                    ps.setTimestamp(6, (Timestamp) row[5]);
                    ps.setTimestamp(7, (Timestamp) row[5]);
                });

        log.info("TestDataInitializer: 기본 더미 데이터 {}개 생성 완료 — accountId {}~{}, commanderId {}~{}",
                count,
                accountIds.get(0), accountIds.get(accountIds.size() - 1),
                charIds.get(0), charIds.get(charIds.size() - 1));
        return charIds;
    }

    // ── PVP 점수 데이터 ────────────────────────────────────────────────────────

    private void ensurePvpData(List<Long> charIds) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pvp_record WHERE commander_id = ?",
                Integer.class, charIds.get(0));
        if (exists != null && exists > 0) {
            log.info("TestDataInitializer: PVP 더미 데이터 이미 존재, 스킵");
            return;
        }

        int total = charIds.size();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> pvpRows = new ArrayList<>(total);
        for (int i = 0; i < total; i++)
            pvpRows.add(new Object[]{charIds.get(i), calcPvpScore(i), now});
        jdbc.batchUpdate(
                "INSERT INTO pvp_record (commander_id, score, wins, losses, last_updated)" +
                " VALUES (?, ?, 0, 0, ?)",
                pvpRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,  (Long)      row[0]);
                    ps.setInt(2,   (int)        row[1]);
                    ps.setTimestamp(3, (Timestamp) row[2]);
                });
        log.info("TestDataInitializer: PVP 더미 데이터 {}개 생성 완료 (base={}, dev={})", total, pvpBaseScore, pvpDeviation);
    }

    // ── Zone 클리어 데이터 ─────────────────────────────────────────────────────

    private void ensureZoneData(List<Long> charIds) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cleared_zone WHERE commander_id = ?",
                Integer.class, charIds.get(0));
        if (exists != null && exists > 0) {
            log.info("TestDataInitializer: Zone 더미 데이터 이미 존재, 스킵");
            return;
        }

        int total = charIds.size();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> zoneRows = new ArrayList<>(total);
        for (int i = 0; i < total; i++)
            zoneRows.add(new Object[]{charIds.get(i), calcZoneName(i), now});
        jdbc.batchUpdate(
                "INSERT INTO cleared_zone (commander_id, zone_name, cleared_at) VALUES (?, ?, ?)",
                zoneRows, BATCH_SIZE, (ps, row) -> {
                    ps.setLong(1,   (Long)      row[0]);
                    ps.setString(2, (String)    row[1]);
                    ps.setTimestamp(3, (Timestamp) row[2]);
                });
        log.info("TestDataInitializer: Zone 더미 데이터 {}개 생성 완료 (base={}, dev={})", total, zoneBase, zoneDeviation);
    }

    // ── 계산 헬퍼 ──────────────────────────────────────────────────────────────

    // i=0 → base, i=1 → base-dev, i=2 → base+dev, i=3 → base-2*dev, i=4 → base+2*dev ...
    private int calcPvpScore(int i) {
        if (i == 0) return pvpBaseScore;
        int half = (i + 1) / 2;
        int sign = (i % 2 == 1) ? -1 : 1;
        return pvpBaseScore + sign * half * pvpDeviation;
    }

    // i=0 → base zone, 이후 동일 패턴으로 인덱스 이동
    private String calcZoneName(int i) {
        int baseIdx = parseZoneIndex(zoneBase);
        if (i == 0) return ZONE_LIST[baseIdx];
        int half = (i + 1) / 2;
        int sign = (i % 2 == 1) ? -1 : 1;
        int idx  = Math.max(0, Math.min(ZONE_LIST.length - 1, baseIdx + sign * half * zoneDeviation));
        return ZONE_LIST[idx];
    }

    // "챕터-스테이지" → ZONE_LIST 인덱스
    private int parseZoneIndex(String zone) {
        String[] parts = zone.split("-");
        int ch = Integer.parseInt(parts[0]);
        int st = Integer.parseInt(parts[1]);
        return (ch - 1) * 10 + (st - 1);
    }

    private String buildEmail(int seq) { return "guest_test" + String.format("%04d", seq); }
}



