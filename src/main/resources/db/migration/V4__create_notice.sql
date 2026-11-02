CREATE TABLE notice (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    title         VARCHAR(200) NOT NULL DEFAULT '',
    body          TEXT         NOT NULL,
    enabled       TINYINT(1)   NOT NULL DEFAULT 0,
    display_order INT          NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
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
