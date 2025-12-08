-- 테스트용 샘플 데이터
-- 계정 ID 1이 존재한다고 가정

-- 샘플 함대 생성
INSERT INTO fleet (account_id, fleet_name, description, is_active) VALUES
(1, 'Main Battle Fleet', '주력 전투 함대', TRUE),
(1, 'Scout Fleet', '정찰 함대', FALSE),
(1, 'Mining Fleet', '채굴 함대', FALSE);

-- 첫 번째 함대의 함선들
INSERT INTO ship (fleet_id, ship_name, position_index, description) VALUES
(1, 'Flagship', 0, '기함'),
(1, 'Escort Alpha', 1, '호위함 A'),
(1, 'Escort Beta', 2, '호위함 B');

-- Flagship의 모듈들 (Body 모듈)
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(1, 0, 5, 0, 1000.0, 0, 0, 0, 50.0, 30.0, 500.0, 5000, 100);

-- Flagship의 무기 모듈들
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(1, 1, 4, 1, 0, 3, 150.0, 2.5, 0, 0, 0, 4000, 80),
(1, 1, 4, 2, 0, 3, 150.0, 2.5, 0, 0, 0, 4000, 80);

-- Flagship의 엔진 모듈
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(1, 2, 3, 3, 0, 0, 0, 0, 100.0, 45.0, 0, 3000, 60);

-- Escort Alpha의 모듈들 (Body 모듈)
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(2, 0, 3, 0, 600.0, 0, 0, 0, 60.0, 40.0, 200.0, 3000, 60);

-- Escort Alpha의 무기 모듈
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(2, 1, 2, 1, 0, 2, 80.0, 2.0, 0, 0, 0, 2000, 40);

-- Escort Alpha의 엔진 모듈
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(2, 2, 2, 2, 0, 0, 0, 0, 120.0, 50.0, 0, 2000, 40);

-- Escort Beta의 모듈들 (Body 모듈)
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(3, 0, 3, 0, 600.0, 0, 0, 0, 60.0, 40.0, 200.0, 3000, 60);

-- Escort Beta의 무기 모듈
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(3, 1, 2, 1, 0, 2, 80.0, 2.0, 0, 0, 0, 2000, 40);

-- Escort Beta의 엔진 모듈
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(3, 2, 2, 2, 0, 0, 0, 0, 120.0, 50.0, 0, 2000, 40);

-- 두 번째 함대 (정찰 함대)의 함선
INSERT INTO ship (fleet_id, ship_name, position_index, description) VALUES
(2, 'Scout Ship', 0, '정찰선');

-- Scout Ship의 모듈들
INSERT INTO ship_module (ship_id, module_type, module_level, slot_index, 
                        health, attack_fire_count, attack_power, attack_cool_time, 
                        movement_speed, rotation_speed, cargo_capacity, 
                        upgrade_money_cost, upgrade_material_cost) VALUES
(4, 0, 2, 0, 400.0, 0, 0, 0, 80.0, 60.0, 100.0, 2000, 40),
(4, 1, 1, 1, 0, 1, 40.0, 1.5, 0, 0, 0, 1000, 20),
(4, 2, 4, 2, 0, 0, 0, 0, 180.0, 80.0, 0, 4000, 80);
