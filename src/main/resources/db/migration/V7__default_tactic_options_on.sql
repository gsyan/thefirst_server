-- 전술 토글 기본값을 전부 OFF(0)에서 전부 ON(63)으로 변경. 기존에 저장된 0은 "의도적으로 전부 끈 것"이 아니라
-- createDefaultFleet이 tacticOptions를 설정하지 않아 생긴 값이므로 전부 ON으로 되돌림.
-- 실제로 보유하지 않은 모듈에 대한 토글은 클라이언트 SpaceFleet.ClearUnavailableTactics가 다음 접속 시 자동으로 꺼서 서버에 재동기화함.
UPDATE fleet SET tactic_options = 63 WHERE tactic_options = 0;
