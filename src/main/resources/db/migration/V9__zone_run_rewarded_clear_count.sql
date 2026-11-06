-- 셀을 클리어할수록 광물 보상이 누적 증가하는 공식(기본값 + 50 × 이번 런 보상 클리어 횟수)의 입력값
ALTER TABLE zone_run
    ADD COLUMN rewarded_clear_count INT NOT NULL DEFAULT 0;
