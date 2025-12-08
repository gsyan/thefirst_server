-- Fleet 테이블 생성
CREATE TABLE fleet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    fleet_name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    b_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    date_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id),
    INDEX idx_account_active (account_id, is_active, b_deleted),
    INDEX idx_account_name (account_id, fleet_name, b_deleted),
    FOREIGN KEY (account_id) REFERENCES account(id)
);

-- Ship 테이블 생성
CREATE TABLE ship (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fleet_id BIGINT NOT NULL,
    ship_name VARCHAR(255) NOT NULL,
    position_index INT NOT NULL,
    description TEXT,
    b_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    date_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fleet_id (fleet_id),
    INDEX idx_fleet_position (fleet_id, position_index, b_deleted),
    FOREIGN KEY (fleet_id) REFERENCES fleet(id)
);

-- ShipModule 테이블 생성
CREATE TABLE ship_module (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ship_id BIGINT NOT NULL,
    module_type INT NOT NULL COMMENT '0: Body, 1: Weapon, 2: Engine',
    module_level INT NOT NULL,
    slot_index INT NOT NULL,
    
    -- 모듈 스탯 정보
    health FLOAT NOT NULL DEFAULT 0,
    attack_fire_count INT NOT NULL DEFAULT 0,
    attack_power FLOAT NOT NULL DEFAULT 0,
    attack_cool_time FLOAT NOT NULL DEFAULT 0,
    movement_speed FLOAT NOT NULL DEFAULT 0,
    rotation_speed FLOAT NOT NULL DEFAULT 0,
    cargo_capacity FLOAT NOT NULL DEFAULT 0,
    upgrade_money_cost INT NOT NULL DEFAULT 0,
    upgrade_material_cost INT NOT NULL DEFAULT 0,
    
    b_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    date_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_ship_id (ship_id),
    INDEX idx_ship_slot (ship_id, slot_index, b_deleted),
    INDEX idx_ship_type (ship_id, module_type, b_deleted),
    FOREIGN KEY (ship_id) REFERENCES ship(id)
);

-- 함대명 유니크 제약조건 (계정별 + 삭제되지 않은 것들 중에서)
ALTER TABLE fleet ADD CONSTRAINT uk_fleet_name_per_account 
UNIQUE (account_id, fleet_name, b_deleted);

-- 함선 위치 유니크 제약조건 (함대별 + 삭제되지 않은 것들 중에서)
ALTER TABLE ship ADD CONSTRAINT uk_ship_position_per_fleet 
UNIQUE (fleet_id, position_index, b_deleted);

-- 함선 모듈 슬롯 유니크 제약조건 (함선별 + 삭제되지 않은 것들 중에서)
ALTER TABLE ship_module ADD CONSTRAINT uk_module_slot_per_ship 
UNIQUE (ship_id, slot_index, b_deleted);
