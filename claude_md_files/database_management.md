# Database Management
# DB 스키마 관리, 컬럼 변경 워크플로우, 실행 방법

---

## 스키마 파일 위치

```
src/main/resources/sql/schema.sql
```

- 전체 테이블 DROP + CREATE 포함
- DB 날리고 재생성할 때 이 파일 하나로 복원
- `fleet_schema.sql`, `sample_fleet_data.sql` 은 구버전 — 무시

---

## ddl-auto 설정

`application-dev.properties`, `application-prod.properties` 모두 `validate` 로 설정됨.
서버 기동 시 Entity와 DB 스키마가 일치하는지만 검증하고, 테이블을 생성/수정하지 않음.

> **주의**: 서버 최초 기동 전 반드시 schema.sql을 DB에 실행해야 함. 안 하면 부팅 실패.

---

## 컬럼 추가/변경 워크플로우

1. `schema.sql` 수정 (CREATE TABLE 정의 업데이트)
2. DB에 `ALTER TABLE` 직접 실행
3. Entity 클래스 수정
4. 서버 재시작 → validate 통과 확인

```sql
-- 예시
ALTER TABLE `character` ADD COLUMN new_col INT NOT NULL DEFAULT 0 AFTER mineral_dark;
```

---

## schema.sql 커맨드라인 실행 (전체 초기화)

```bash
mysql -u root -p12121212 GameDB < "D:/BK/thefirst/thefirst_server/src/main/resources/sql/schema.sql"
```

`mysql` 이 PATH에 없으면 직접 경로 지정:

```bash
"C:/Program Files/MariaDB 11.x/bin/mysql.exe" -u root -p12121212 GameDB < "D:/BK/thefirst/thefirst_server/src/main/resources/sql/schema.sql"
```
