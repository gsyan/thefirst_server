-- 지휘관 경험치는 셀 클리어마다 적립되지 않고 존 탈출 확정 시 zone_grid.commander_exp_reward를 1회만 지급하는 방식으로 변경되어 더 이상 쓰이지 않음
ALTER TABLE zone_run
    DROP COLUMN commander_exp_banked;
