-- fleet 테이블에 전술 옵션 컬럼 추가
-- IF NOT EXISTS: 로컬/prod 기존 DB에 이미 컬럼이 있어도 에러 없이 통과
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS tactic_options INT NOT NULL DEFAULT 0;
