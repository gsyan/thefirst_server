-- GameDB 전체 스키마
-- Hibernate 6 + Spring Boot 3 + MariaDB 기준 (SpringPhysicalNamingStrategy 적용)
-- 사용법: validate 모드 전환 전 또는 DB 초기화 시 수동으로 실행

-- ============================================================
-- 초기화 (FK 의존성 역순으로 DROP)
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS ship_module;
DROP TABLE IF EXISTS ship;
DROP TABLE IF EXISTS fleet;
DROP TABLE IF EXISTS cleared_zone;
DROP TABLE IF EXISTS redeem_code_usage;
DROP TABLE IF EXISTS module_research;
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
    mineral                 INT             NOT NULL DEFAULT 0,
    commander_level         INT             NOT NULL DEFAULT 1,
    exp                     INT             NOT NULL DEFAULT 0,
    module_point            INT             NOT NULL DEFAULT 0,
    module_point_max_got    INT             NOT NULL DEFAULT 0,
    pvp_point               INT             NOT NULL DEFAULT 0,
    pvp_point_max_got       INT             NOT NULL DEFAULT 0,
    pvp_point_expiry        DATETIME(6)         NULL,
    pvp_point_season_ref    INT             NOT NULL DEFAULT 0,
    name_change_count       INT             NOT NULL DEFAULT 2,
    collect_date_time       DATETIME(6)         NULL,
    last_online_at          DATETIME(6)         NULL,
    claimed_days_mask       INT             NOT NULL DEFAULT 0,
    vip_claimed_days_mask   INT             NOT NULL DEFAULT 0,
    login_reward_month      INT                 NULL,
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
    invested_module_point   INT             NOT NULL DEFAULT 0,
    invested_mineral        INT             NOT NULL DEFAULT 0,
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
-- module_research
-- 문자열 기반 모듈 연구 상태 저장
-- ============================================================
CREATE TABLE module_research (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    commander_id    BIGINT          NOT NULL,
    research_id     VARCHAR(255)        NULL,
    researched      TINYINT(1)      NOT NULL DEFAULT 0,
    created         DATETIME(6)     NOT NULL,
    modified        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_research_commander (commander_id)
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
    first_bonus_claimed  TINYINT(1)   NOT NULL DEFAULT 0, -- 영구: techPoint/modulePoint 최초 지급 후 1, 리셋 없음
    PRIMARY KEY (id),
    UNIQUE KEY uk_cleared_zone (commander_id, zone_name),
    INDEX idx_cleared_commander (commander_id)
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
    OUT p_mineral       INT,
    OUT p_pvp_point     INT
)
BEGIN
    SELECT mineral, pvp_point
    INTO   p_mineral, p_pvp_point
    FROM   commander
    WHERE  id = p_commander_id AND deleted = 0;
END$$

DELIMITER ;


