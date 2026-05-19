-- GameDB 초기 스키마 (Flyway V1)
-- 신규 DB 초기화용 — DROP/IF NOT EXISTS/SP 없음

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
-- character  (MariaDB 예약어 — 백틱 필수)
-- ============================================================
CREATE TABLE `character` (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    account_id              BIGINT          NOT NULL,
    character_name          VARCHAR(255)    NOT NULL,
    last_location           BIGINT              NULL,
    mineral                 INT             NOT NULL DEFAULT 0,
    tech_point              INT             NOT NULL DEFAULT 0,
    module_point            INT             NOT NULL DEFAULT 0,
    module_point_max_got    INT             NOT NULL DEFAULT 0,
    pvp_point               INT             NOT NULL DEFAULT 0,
    pvp_point_max_got       INT             NOT NULL DEFAULT 0,
    pvp_point_expiry        DATETIME(6)         NULL,
    pvp_point_season_ref    INT             NOT NULL DEFAULT 0,
    name_change_count       INT             NOT NULL DEFAULT 2,
    collect_date_time       DATETIME(6)         NULL,
    last_online_at          DATETIME(6)         NULL,
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0,
    date_time               DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_character_name (character_name),
    INDEX idx_character_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- fleet
-- ============================================================
CREATE TABLE fleet (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    character_id    BIGINT          NOT NULL,
    fleet_name      VARCHAR(255)    NOT NULL,
    description     TEXT                NULL,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    formation       VARCHAR(255)    NOT NULL,
    tactic_options  INT             NOT NULL DEFAULT 0,
    created         DATETIME(6)     NOT NULL,
    modified        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_fleet_character (character_id)
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
    current_health          FLOAT           NOT NULL DEFAULT 0,
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0,
    created                 DATETIME(6)     NOT NULL,
    modified                DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ship_module_ship FOREIGN KEY (ship_id) REFERENCES ship (id),
    INDEX idx_module_ship (ship_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- ship_module_level
-- ============================================================
CREATE TABLE ship_module_level (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    ship_id         BIGINT          NOT NULL,
    body_index      INT             NOT NULL,
    module_type     VARCHAR(100)    NOT NULL,
    slot_index      INT             NOT NULL,
    module_sub_type VARCHAR(100)    NOT NULL,
    level           INT             NOT NULL DEFAULT 1,
    created         DATETIME(6)     NOT NULL,
    modified        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sml_ship FOREIGN KEY (ship_id) REFERENCES ship (id),
    UNIQUE KEY uk_sml (ship_id, body_index, module_type, slot_index, module_sub_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- progress
-- ============================================================
CREATE TABLE progress (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    character_id            BIGINT          NOT NULL,
    category                VARCHAR(50)     NOT NULL,
    progress_key            VARCHAR(100)    NOT NULL,
    value                   INT                 NULL,
    completed_date_time     DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_progress (character_id, category, progress_key),
    INDEX idx_progress_character (character_id)
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
-- pvp_record
-- ============================================================
CREATE TABLE pvp_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    character_id    BIGINT          NOT NULL,
    score           INT             NOT NULL DEFAULT 1000,
    wins            INT             NOT NULL DEFAULT 0,
    losses          INT             NOT NULL DEFAULT 0,
    last_updated    DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pvp_character (character_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- module_research
-- ============================================================
CREATE TABLE module_research (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    character_id    BIGINT          NOT NULL,
    module_type     VARCHAR(100)        NULL,
    module_sub_type VARCHAR(100)        NULL,
    research_id     VARCHAR(255)        NULL,
    researched      TINYINT(1)      NOT NULL DEFAULT 0,
    created         DATETIME(6)     NOT NULL,
    modified        DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_research_character (character_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- cleared_zone
-- ============================================================
CREATE TABLE cleared_zone (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    character_id         BIGINT       NOT NULL,
    zone_name            VARCHAR(255) NOT NULL,
    cleared_at           DATETIME(6)  NOT NULL,
    reward_claimed       TINYINT(1)   NOT NULL DEFAULT 0,
    first_bonus_claimed  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cleared_zone (character_id, zone_name),
    INDEX idx_cleared_character (character_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
