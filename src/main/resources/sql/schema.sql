-- GameDB 전체 스키마
-- Hibernate 6 + Spring Boot 3 + MariaDB 기준 (SpringPhysicalNamingStrategy 적용)
-- 사용법: validate 모드 전환 전 또는 DB 초기화 시 수동으로 실행

-- ============================================================
-- 초기화 (FK 의존성 역순으로 DROP)
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS commander_fleet_preset_slot_module;
DROP TABLE IF EXISTS commander_fleet_preset_slot;
DROP TABLE IF EXISTS commander_fleet_preset;
DROP TABLE IF EXISTS ship_module;
DROP TABLE IF EXISTS ship;
DROP TABLE IF EXISTS fleet;
DROP TABLE IF EXISTS zone_cell_clear_log;
DROP TABLE IF EXISTS zone_run;
DROP TABLE IF EXISTS cleared_zone;
DROP TABLE IF EXISTS redeem_code_usage;
DROP TABLE IF EXISTS pvp_record;
DROP TABLE IF EXISTS pvp_season;
DROP TABLE IF EXISTS progress;
DROP TABLE IF EXISTS vip_subscription;
DROP TABLE IF EXISTS commander;
DROP TABLE IF EXISTS account;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- account
-- ============================================================
CREATE TABLE account (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    google_id   VARCHAR(255)        NULL,
    deleted     TINYINT(1)      NOT NULL DEFAULT 0,
    date_time   DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_email     (email),
    UNIQUE KEY uk_account_google_id (google_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- commander
-- ============================================================
CREATE TABLE commander (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    account_id              BIGINT          NOT NULL,
    commander_name          VARCHAR(255)    NOT NULL,
    last_location           BIGINT              NULL,
    commander_level         INT             NOT NULL DEFAULT 1,
    exp                     INT             NOT NULL DEFAULT 0,
    pvp_point               INT             NOT NULL DEFAULT 0,
    pvp_point_max_got       INT             NOT NULL DEFAULT 0,
    pvp_point_expiry        DATETIME(6)         NULL,
    pvp_point_season_ref    INT             NOT NULL DEFAULT 0,
    name_change_count       INT             NOT NULL DEFAULT 2,
    command_power_max       INT             NOT NULL DEFAULT 300,
    exploration_point           INT         NOT NULL DEFAULT 0,
    highest_cleared_zone_number INT         NOT NULL DEFAULT 0,
    collect_date_time       DATETIME(6)         NULL,
    last_online_at          DATETIME(6)         NULL,
    claimed_days_mask       INT             NOT NULL DEFAULT 0,
    vip_claimed_days_mask   INT             NOT NULL DEFAULT 0,
    login_reward_month      INT                 NULL,
    last_daily_claim_date   DATE                NULL,
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0,
    date_time               DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commander_name (commander_name),
    INDEX idx_commander_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- fleet
-- ============================================================
CREATE TABLE fleet (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    commander_id    BIGINT          NOT NULL,
    fleet_name      VARCHAR(255)    NOT NULL,
    description     TEXT                NULL,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    formation       VARCHAR(255)    NOT NULL,
    tactic_options  INT             NOT NULL DEFAULT 0,
    created         DATETIME(6)     NOT NULL,
    modified        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_fleet_commander (commander_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- commander_fleet_preset (프리셋 기반 신규 함대 시스템 — presetIndex=0이 현재 기본 활성 함대)
-- ============================================================
CREATE TABLE commander_fleet_preset (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    commander_id    BIGINT          NOT NULL,
    preset_index    INT             NOT NULL,
    created         DATETIME(6)     NOT NULL,
    modified        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_fleet_preset_commander (commander_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- commander_fleet_preset_slot
-- ============================================================
CREATE TABLE commander_fleet_preset_slot (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    fleet_preset_id     BIGINT          NOT NULL,
    slot_index          INT             NOT NULL,
    ship_preset_id      VARCHAR(255)    NOT NULL,
    is_front            TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_fleet_preset_slot_preset FOREIGN KEY (fleet_preset_id) REFERENCES commander_fleet_preset (id),
    INDEX idx_fleet_preset_slot_preset (fleet_preset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- commander_fleet_preset_slot_module — 슬롯(함선) 하나에 유저가 실제로 장착한 모듈(빔/미사일/격납고, on/off만) — row 존재=장착
-- ============================================================
CREATE TABLE commander_fleet_preset_slot_module (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    preset_slot_id      BIGINT          NOT NULL,
    module_type         VARCHAR(100)    NOT NULL,
    slot_index          INT             NOT NULL,
    module_sub_type     VARCHAR(100)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_fleet_preset_slot_module_slot FOREIGN KEY (preset_slot_id) REFERENCES commander_fleet_preset_slot (id),
    INDEX idx_fleet_preset_slot_module_slot (preset_slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- ship
-- ============================================================
CREATE TABLE ship (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    fleet_id        BIGINT          NOT NULL,
    ship_name       VARCHAR(255)    NOT NULL,
    position_index  INT             NOT NULL,
    description     TEXT                NULL,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    created         DATETIME(6)     NOT NULL,
    modified        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ship_fleet FOREIGN KEY (fleet_id) REFERENCES fleet (id),
    INDEX idx_ship_fleet (fleet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- ship_module
-- ============================================================
CREATE TABLE ship_module (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    ship_id                 BIGINT          NOT NULL,
    module_type             VARCHAR(100)    NOT NULL,
    module_sub_type         VARCHAR(100)    NOT NULL,
    module_level            INT             NOT NULL,
    body_index              INT             NOT NULL,
    slot_index              INT             NOT NULL,
    current_health          FLOAT           NOT NULL DEFAULT 0,
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0,
    created                 DATETIME(6)     NOT NULL,
    modified                DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ship_module_ship FOREIGN KEY (ship_id) REFERENCES ship (id),
    INDEX idx_module_ship (ship_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- progress
-- 유니크: commander_id + category + progress_key
-- ============================================================
CREATE TABLE progress (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    commander_id            BIGINT          NOT NULL,
    category                VARCHAR(50)     NOT NULL,
    progress_key            VARCHAR(100)    NOT NULL,
    value                   INT                 NULL,
    completed_date_time     DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_progress (commander_id, category, progress_key),
    INDEX idx_progress_commander (commander_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- pvp_season
-- ============================================================
CREATE TABLE pvp_season (
    season_number       INT             NOT NULL,
    start_time          DATETIME(6)         NULL,
    end_time            DATETIME(6)     NOT NULL,
    reward_distributed  TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (season_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- pvp_record  (Redis 동기화용 백업)
-- ============================================================
CREATE TABLE pvp_record (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    commander_id            BIGINT          NOT NULL,
    score                   INT             NOT NULL DEFAULT 1000,
    wins                    INT             NOT NULL DEFAULT 0,
    losses                  INT             NOT NULL DEFAULT 0,
    last_updated            DATETIME(6)     NOT NULL,
    last_rewarded_season    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pvp_commander (commander_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- vip_subscription  (캐릭터당 1행, UPSERT로 관리)
-- ============================================================
CREATE TABLE vip_subscription (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    commander_id            BIGINT          NOT NULL,
    vip_expiry              DATETIME(6)     NOT NULL,
    purchase_token          VARCHAR(512)    NOT NULL,
    platform                VARCHAR(32)     NOT NULL,
    updated_at              DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vip_commander_id (commander_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- cleared_zone
-- 유니크: commander_id + zone_name
-- ============================================================
CREATE TABLE cleared_zone (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    commander_id         BIGINT       NOT NULL,
    zone_name            VARCHAR(255) NOT NULL,
    cleared_at           DATETIME(6)  NOT NULL,
    reward_claimed       TINYINT(1)   NOT NULL DEFAULT 0, -- per-run: clearZoneStage→0, claimZoneReward→1
    first_bonus_claimed  TINYINT(1)   NOT NULL DEFAULT 0, -- 영구: 보상 최초 지급 후 1, 리셋 없음
    PRIMARY KEY (id),
    UNIQUE KEY uk_cleared_zone (commander_id, zone_name),
    INDEX idx_cleared_commander (commander_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- zone_run — 탐사 그리드 존 진행(런). 커맨더당 status='IN_PROGRESS' 행은 항상 최대 1개(애플리케이션에서 보장)
-- ============================================================
CREATE TABLE zone_run (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    commander_id                BIGINT       NOT NULL,
    zone_number                 INT          NOT NULL,
    status                      VARCHAR(255) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS/ESCAPED/ABANDONED (EFormationType처럼 길이 미지정 문자열 enum 컬럼 관례와 동일)
    reward_claimed              TINYINT(1)   NOT NULL DEFAULT 0,
    exploration_point_banked    INT          NOT NULL DEFAULT 0,
    commander_exp_banked        INT          NOT NULL DEFAULT 0,
    current_cell                VARCHAR(20)  NOT NULL, -- "x-y" 형식(0-indexed), 마지막으로 클리어한 셀
    fleet_health_snapshot_json  TEXT             NULL, -- 마지막 셀 클리어 시점의 내 함대 체력 비율(슬롯 포지션 인덱스별) JSON — 재접속 시 손상 상태 복구용
    started_at                  DATETIME(6)  NOT NULL,
    ended_at                    DATETIME(6)      NULL,
    PRIMARY KEY (id),
    INDEX idx_zone_run_commander (commander_id),
    INDEX idx_zone_run_commander_status (commander_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- zone_cell_clear_log — zone_run 하나 안에서 셀을 클리어한 순서/시각 기록(재접속 진행 복구용)
-- ============================================================
CREATE TABLE zone_cell_clear_log (
    id                            BIGINT       NOT NULL AUTO_INCREMENT,
    zone_run_id                   BIGINT       NOT NULL,
    cell                          VARCHAR(20)  NOT NULL, -- "x-y" 형식(0-indexed)
    cleared_at                    DATETIME(6)  NOT NULL,
    reward_card_candidates_json   TEXT             NULL, -- 이 셀 클리어 시 추첨된 보상카드 후보 3개(cardId) JSON — 후보 없는 셀(탈출/빈 셀)은 NULL
    reward_card_selected_id       VARCHAR(64)      NULL, -- 확정 선택한 cardId — 아직 선택 전이면 NULL(재접속 시 이 상태로 카드 선택 팝업 복구)
    PRIMARY KEY (id),
    INDEX idx_zone_cell_clear_log_run (zone_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- app_config: 운영 설정값 저장 (버전 체크 등)
CREATE TABLE app_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(64)  NOT NULL,
    config_value VARCHAR(256) NOT NULL,
    description  VARCHAR(256),
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- redeem_code_usage
-- 유니크: commander_id + code (동일 커맨더의 동일 코드 중복 사용 방지)
-- ============================================================
CREATE TABLE redeem_code_usage (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    commander_id  BIGINT       NOT NULL,
    code          VARCHAR(64)  NOT NULL,
    used_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_redeem_code_usage (commander_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- MariaDB Stored Procedure 예시
-- MSSQL의 SP와 거의 동일한 문법
-- Spring에서: @Query(nativeQuery=true, value="CALL proc_name(:param)")
--             또는 JdbcTemplate.execute("CALL proc_name(?)", ...)
-- ============================================================

DELIMITER $$

-- 커맨더의 자원 현황 조회 예시 SP
DROP PROCEDURE IF EXISTS sp_get_commander_resources$$
CREATE PROCEDURE sp_get_commander_resources(
    IN  p_commander_id  BIGINT,
    OUT p_pvp_point     INT
)
BEGIN
    SELECT pvp_point
    INTO   p_pvp_point
    FROM   commander
    WHERE  id = p_commander_id AND deleted = 0;
END$$

DELIMITER ;


