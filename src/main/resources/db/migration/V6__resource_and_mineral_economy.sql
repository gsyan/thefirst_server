-- 전술력(tactic_power) 개념을 자원(resource)으로 전면 개명
ALTER TABLE commander
    CHANGE COLUMN tactic_power_max resource_max INT NOT NULL DEFAULT 1000;

ALTER TABLE zone_run
    CHANGE COLUMN tactic_power resource INT NOT NULL DEFAULT 0;

-- 탐사포인트(영구 은행 재화) 경제를 폐지하고 광물(존런 한정, 종료 시 소멸) 경제로 대체
ALTER TABLE commander
    DROP COLUMN exploration_point,
    DROP COLUMN exploration_point_earned_total,
    ADD COLUMN mineral_earned_total INT NOT NULL DEFAULT 0;

ALTER TABLE zone_run
    DROP COLUMN exploration_point_banked;
