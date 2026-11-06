-- 보상카드 리롤의 하루 총량 제한을 폐지하고, 시간 경과로 자연 충전되는 횟수(계정 공유, 존런 무관)로 교체
ALTER TABLE commander
    DROP COLUMN reward_card_reroll_count_today,
    DROP COLUMN reward_card_reroll_reset_date,
    ADD COLUMN reward_card_reroll_charge INT NOT NULL DEFAULT 10,
    ADD COLUMN reward_card_reroll_charge_synced_at DATETIME(6) NULL;

-- 보상카드를 1회 선택 확정하는 방식에서 광물로 여러 장 구매하는 방식으로 전환 — 셀 클리어 1회당 여러 장 구매 가능해져 단일 컬럼으로는 표현 불가
ALTER TABLE zone_cell_clear_log
    DROP COLUMN reward_card_selected_id,
    ADD COLUMN reward_card_purchased_ids_json TEXT NULL,
    ADD COLUMN reward_card_popup_closed BOOLEAN NOT NULL DEFAULT FALSE;
