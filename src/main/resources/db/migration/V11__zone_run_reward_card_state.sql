-- 보상카드 후보/구매이력/팝업닫힘상태를 셀 클리어 로그가 아니라 존런 단위로 고정(셀을 넘어가도 후보가 바뀌지 않게)
ALTER TABLE zone_run
    ADD COLUMN reward_card_candidates_json TEXT,
    ADD COLUMN reward_card_purchased_ids_json TEXT,
    ADD COLUMN reward_card_popup_closed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN pending_health_heal_bonus FLOAT NOT NULL DEFAULT 0;

ALTER TABLE zone_cell_clear_log
    DROP COLUMN reward_card_candidates_json,
    DROP COLUMN reward_card_purchased_ids_json,
    DROP COLUMN reward_card_popup_closed;
