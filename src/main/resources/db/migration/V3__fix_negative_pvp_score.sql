-- 하한 없이 감점되어 음수가 된 PVP 점수를 0으로 보정
UPDATE pvp_record SET score = 0 WHERE score < 0;
