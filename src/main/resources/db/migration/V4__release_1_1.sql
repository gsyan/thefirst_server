-- V3(테스트/실서버 배포본) 이후의 모든 스키마 변경 — 배포 전까지 이 파일 하나에 모아서 수정한다. 배포하는 순간 동결하고 다음 변경은 V5로 올린다.

-- ============================================================
-- V4__legacy_hull_refund_and_cleanup
-- ============================================================
-- 옛 형식 함체 ID(hull_{tier}_1_{slots})는 현재 데이터테이블에 없어 그 언락 기록은 효력이 없음 — 언락에 쓴 업적포인트(함체 + 실드/요격체)를
-- 옛 데이터테이블 비용 그대로 환급한 뒤 기록을 삭제. 실드/요격체 언락 컬럼을 지우는 M1보다 반드시 먼저 실행해야 함
UPDATE commander c
JOIN (
    SELECT commander_id,
        SUM(
            CASE hull_sub_type
                WHEN 'hull_4_1_21100'  THEN 500
                WHEN 'hull_5_1_22100'  THEN 2000
                WHEN 'hull_6_1_22211'  THEN 4000
                WHEN 'hull_7_1_32211'  THEN 8000
                WHEN 'hull_8_1_33211'  THEN 16000
                WHEN 'hull_9_1_33311'  THEN 32000
                WHEN 'hull_10_1_43311' THEN 64000
                WHEN 'hull_11_1_44311' THEN 128000
                WHEN 'hull_12_1_44411' THEN 256000
                WHEN 'hull_13_1_54411' THEN 512000
                WHEN 'hull_14_1_55411' THEN 1024000
                ELSE 0
            END
            + (shield_unlocked + interceptor_unlocked) *
            CASE hull_sub_type
                WHEN 'hull_6_1_22211'  THEN 500
                WHEN 'hull_7_1_32211'  THEN 1000
                WHEN 'hull_8_1_33211'  THEN 2000
                WHEN 'hull_9_1_33311'  THEN 4000
                WHEN 'hull_10_1_43311' THEN 8000
                WHEN 'hull_11_1_44311' THEN 16000
                WHEN 'hull_12_1_44411' THEN 32000
                WHEN 'hull_13_1_54411' THEN 64000
                WHEN 'hull_14_1_55411' THEN 128000
                ELSE 0
            END
        ) AS refund_point
    FROM commander_unlocked_hull
    WHERE hull_sub_type REGEXP '^hull_[0-9]+_1_[0-9]+$'
    GROUP BY commander_id
) refund ON refund.commander_id = c.id
SET c.achievement_point = c.achievement_point + refund.refund_point;

DELETE FROM commander_unlocked_hull
WHERE hull_sub_type REGEXP '^hull_[0-9]+_1_[0-9]+$';

-- 옛 형식 함체 함선의 모듈 행 삭제 — 함선 자체는 서버가 함대 조회 시 기본 함체로 교체하고 빈 모듈을 기본 레벨1로 채움(FleetService.heal*)
DELETE m FROM module m
JOIN ship s ON s.id = m.ship_id
WHERE s.hull_sub_type REGEXP '^hull_[0-9]+_1_[0-9]+$';

-- 진행 중 존런 삭제 — 자원/광물/보상카드 상태를 이어갈 수 없음. 끝난 존런(ESCAPED/ABANDONED)은 일일퀘스트 집계에 쓰이므로 유지
DELETE l FROM zone_cell_clear_log l
JOIN zone_run r ON r.id = l.zone_run_id
WHERE r.status = 'IN_PROGRESS';

DELETE FROM zone_run
WHERE status = 'IN_PROGRESS';

-- ============================================================
-- V4__create_notice
-- ============================================================
CREATE TABLE notice (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    enabled       TINYINT(1)   NOT NULL DEFAULT 0,
    display_order INT          NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 공지 언어별 제목/본문 — 클라가 현재 언어(locale)로 골라 표시
CREATE TABLE notice_text (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    notice_id     BIGINT       NOT NULL,
    locale        VARCHAR(10)  NOT NULL,
    title         VARCHAR(200) NOT NULL DEFAULT '',
    body          TEXT         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_locale (notice_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE commander_notice_seen (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    commander_id  BIGINT      NOT NULL,
    notice_id     BIGINT      NOT NULL,
    seen_version  INT         NOT NULL,
    seen_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commander_notice (commander_id, notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 함선 시스템 대개편 안내 공지 — id=1 고정(이후 마이그레이션에서 WHERE id = 1로 수정할 수 있게). SQL로 문구를 고칠 땐 version도 +1
INSERT INTO notice (id, enabled, display_order, version, created_at, updated_at)
VALUES (1, 1, 0, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO notice_text (notice_id, locale, title, body) VALUES
(1, 'ko', '함선 시스템 대개편 안내',
'함선/모듈 시스템이 전면 개편되었습니다.

■ 주요 변경
- 함체가 13종 아키타입으로 개편되었고, 아키타입마다 4단계 패시브가 추가되었습니다.
- 기존 함대는 켄투리온함 1척(기본 모듈)으로 재편성됩니다.
- 진행 중이던 탐사는 종료되었습니다.

■ 보상
- 기존 함체 해금에 사용한 AP는 전액 환급되었습니다.
- 지휘관 레벨은 그대로 유지됩니다.

■ 삭제된 시스템
- 탐험포인트, 구매지휘력 시스템은 삭제되었으며 관련 보상은 없습니다.
- 지휘력으로 진행한 모듈 강화는 보상되지 않습니다.'),
(1, 'en', 'Major Ship System Overhaul',
'The ship and module systems have been completely overhauled.

■ Key Changes
- Hulls have been reorganized into 13 archetypes, and each archetype now has a 4-stage passive.
- Your existing fleet has been reorganized into one Centurion with default modules.
- Any exploration in progress has ended.

■ Compensation
- All AP spent on unlocking hulls under the old system has been refunded in full.
- Your commander level stays the same.

■ Removed Systems
- Exploration Points and purchased Command Power have been removed. There is no compensation for them.
- Module upgrades made with Command Power are not compensated.');

-- ============================================================
-- V5__ship_module_combat_overhaul
-- ============================================================
-- M1: 실드/요격체 언락 제도 폐지(선체 언락은 유지)
ALTER TABLE commander_unlocked_hull
    DROP COLUMN shield_unlocked,
    DROP COLUMN interceptor_unlocked;

-- M2: 모듈 영구강화(지휘력 소모, 슬롯단위) 폐지
ALTER TABLE module
    DROP COLUMN attack_points,
    DROP COLUMN attack_to_fighter_points,
    DROP COLUMN fire_rate_points,
    DROP COLUMN silence_points,
    DROP COLUMN ammo_points,
    DROP COLUMN health_points,
    DROP COLUMN disrupt_points;

-- M3: 아키타입 패시브 단계별 언락 진행도 — (지휘관, 아키타입, 단계)마다 1행, 앞 단계가 완료돼야 다음 단계가 0부터 세기 시작함
-- counter_value는 이 단계 안에서의 진행도(문턱까지만 저장, 넘친 값은 버림), completed는 한 번 true가 되면 영구
CREATE TABLE commander_hull_archetype_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    commander_id BIGINT NOT NULL,
    hull_archetype VARCHAR(50) NOT NULL,
    stage INT NOT NULL,
    counter_type VARCHAR(50) NOT NULL,
    counter_value BIGINT NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commander_archetype_stage (commander_id, hull_archetype, stage)
);

-- M5: 광물 재화(zone_run 스코프, tactic_power와 동일한 신뢰모델로 동기화)
ALTER TABLE zone_run
    ADD COLUMN mineral_amount INT NOT NULL DEFAULT 0;

-- M6/M13: 아키타입 단위 영구 모듈강화(업적포인트 소모) — beam/missile/hangar는 슬롯(slot_index)마다 레벨이 독립, hull/shield/interceptor는 슬롯 개념이 없어 slot_index=0 고정
CREATE TABLE commander_archetype_module_upgrade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    commander_id BIGINT NOT NULL,
    hull_archetype VARCHAR(50) NOT NULL,
    module_type VARCHAR(30) NOT NULL,
    slot_index INT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commander_archetype_module (commander_id, hull_archetype, module_type, slot_index)
);

-- M13: 지휘력(CP) 캐파를 커맨더 레벨 기반 자동 계산으로 전환 — 구매(탐험포인트 소모)로 영구 저장하던 컬럼 폐기
ALTER TABLE commander
    DROP COLUMN command_power_max;

-- ============================================================
-- V6__resource_and_mineral_economy
-- ============================================================
-- 전술력(tactic_power) 개념을 자원(resource)으로 전면 개명
ALTER TABLE commander
    CHANGE COLUMN tactic_power_max resource_max INT NOT NULL DEFAULT 10000;

ALTER TABLE zone_run
    CHANGE COLUMN tactic_power resource INT NOT NULL DEFAULT 0;

-- 자원 단위 10배(전술 비용 10배와 짝) — 기존 지휘관 최대치와 진행 중인 런의 현재치를 같은 비율로 환산
UPDATE commander SET resource_max = resource_max * 10;
UPDATE zone_run SET resource = resource * 10;

-- 탐사포인트(영구 은행 재화) 경제를 폐지하고 광물(존런 한정, 종료 시 소멸) 경제로 대체
ALTER TABLE commander
    DROP COLUMN exploration_point,
    DROP COLUMN exploration_point_earned_total,
    ADD COLUMN mineral_earned_total INT NOT NULL DEFAULT 0;

ALTER TABLE zone_run
    DROP COLUMN exploration_point_banked;

-- ============================================================
-- V7__default_tactic_options_on
-- ============================================================
-- 전술 토글 기본값을 전부 OFF(0)에서 전부 ON(63)으로 변경. 기존에 저장된 0은 "의도적으로 전부 끈 것"이 아니라
-- createDefaultFleet이 tacticOptions를 설정하지 않아 생긴 값이므로 전부 ON으로 되돌림.
-- 실제로 보유하지 않은 모듈에 대한 토글은 클라이언트 SpaceFleet.ClearUnavailableTactics가 다음 접속 시 자동으로 꺼서 서버에 재동기화함.
UPDATE fleet SET tactic_options = 63 WHERE tactic_options = 0;

-- ============================================================
-- V8__reward_card_purchase_and_reroll_charge
-- ============================================================
-- 보상카드 리롤의 하루 총량 제한을 폐지하고, 시간 경과로 자연 충전되는 횟수(계정 공유, 존런 무관)로 교체
ALTER TABLE commander
    DROP COLUMN reward_card_reroll_count_today,
    DROP COLUMN reward_card_reroll_reset_date,
    ADD COLUMN reward_card_reroll_charge INT NOT NULL DEFAULT 10,
    ADD COLUMN reward_card_reroll_charge_synced_at DATETIME(6) NULL;

-- 보상카드를 1회 선택 확정하는 방식에서 광물로 여러 장 구매하는 방식으로 전환 — 셀 클리어 1회당 여러 장 구매 가능해져 단일 컬럼으로는 표현 불가
ALTER TABLE zone_cell_clear_log
    DROP COLUMN reward_card_selected_id,
    ADD COLUMN reward_card_purchased_ids_json TEXT NULL,
    ADD COLUMN reward_card_popup_closed BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- V9__zone_run_rewarded_clear_count
-- ============================================================
-- 셀을 클리어할수록 광물 보상이 누적 증가하는 공식(기본값 + 50 × 이번 런 보상 클리어 횟수)의 입력값
ALTER TABLE zone_run
    ADD COLUMN rewarded_clear_count INT NOT NULL DEFAULT 0;

-- ============================================================
-- V10__zone_run_mineral_flat_bonus_total
-- ============================================================
-- Buff_MineralFlatBonus 보상카드 구매로 누적되는 "앞으로 셀 클리어마다 가산할 고정 광물" 합계 — 가산량은 card.value1이 아니라 구매 시 지불한 비용 그대로
ALTER TABLE zone_run
    ADD COLUMN mineral_flat_bonus_total INT NOT NULL DEFAULT 0;

-- ============================================================
-- V11__zone_run_reward_card_state
-- ============================================================
-- 보상카드 후보/구매이력/팝업닫힘상태를 셀 클리어 로그가 아니라 존런 단위로 고정(셀을 넘어가도 후보가 바뀌지 않게)
ALTER TABLE zone_run
    ADD COLUMN reward_card_candidates_json TEXT,
    ADD COLUMN reward_card_purchased_ids_json TEXT,
    ADD COLUMN reward_card_popup_closed BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE zone_cell_clear_log
    DROP COLUMN reward_card_candidates_json,
    DROP COLUMN reward_card_purchased_ids_json,
    DROP COLUMN reward_card_popup_closed;

-- ============================================================
-- V12__drop_zone_run_commander_exp_banked
-- ============================================================
-- 지휘관 경험치는 셀 클리어마다 적립되지 않고 존 탈출 확정 시 zone_grid.commander_exp_reward를 1회만 지급하는 방식으로 변경되어 더 이상 쓰이지 않음
ALTER TABLE zone_run
    DROP COLUMN commander_exp_banked;

-- ============================================================
-- V13__create_commander_daily_counter
-- ============================================================
-- commander_daily_counter: 일일퀘스트 판정용 날짜(UTC)별 범용 카운터
-- counter_type = EAchievementConditionType 이름(RewardCardPurchase, MineralEarned 등), 이벤트 발생 시 upsert로 누적
CREATE TABLE commander_daily_counter (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    commander_id   BIGINT       NOT NULL,
    counter_date   DATE         NOT NULL,
    counter_type   VARCHAR(64)  NOT NULL,
    counter_value  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commander_daily_counter (commander_id, counter_date, counter_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- V14__commander_daily_quest_streak
-- ============================================================
-- 일일퀘스트 연속 달성: 3개를 모두 수령한 연속 일수와 마지막 완료일(UTC)
ALTER TABLE commander
    ADD COLUMN daily_quest_streak INT NOT NULL DEFAULT 0,
    ADD COLUMN daily_quest_streak_date DATE NULL;

-- 일일퀘스트 목록 고정: 그날(UTC) 처음 뽑은 업적 ID 목록(쉼표 구분)을 저장해 하루 동안 바뀌지 않게 함
ALTER TABLE commander
    ADD COLUMN daily_quest_date DATE NULL,
    ADD COLUMN daily_quest_ids VARCHAR(255) NULL;

-- ============================================================
-- V15__zone_run_continuous_archetypes
-- ============================================================
-- 존런 시작부터 지금까지 계속 함대에 있었던 아키타입 목록(쉼표 구분) — 탈출 성공 정산 시 아키타입 패시브 zone_run_clear 카운터 증가 대상
ALTER TABLE zone_run
    ADD COLUMN continuous_archetypes TEXT NULL;
