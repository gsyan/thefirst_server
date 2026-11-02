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

-- M3: 아키타입 패시브 언락 조건 카운터
CREATE TABLE commander_hull_archetype_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    commander_id BIGINT NOT NULL,
    hull_archetype VARCHAR(50) NOT NULL,
    counter_type VARCHAR(50) NOT NULL,
    counter_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commander_archetype_counter (commander_id, hull_archetype, counter_type)
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
