-- Buff_MineralFlatBonus 보상카드 구매로 누적되는 "앞으로 셀 클리어마다 가산할 고정 광물" 합계 — 가산량은 card.value1이 아니라 구매 시 지불한 비용 그대로
ALTER TABLE zone_run
    ADD COLUMN mineral_flat_bonus_total INT NOT NULL DEFAULT 0;
