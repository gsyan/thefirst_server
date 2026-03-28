# Database Management
# DB 스키마 관리, 컬럼 변경 워크플로우, 실행 방법, Jenkins DB 재생성 파이프라인

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

---

## Jenkins 서버 빌드 파이프라인 (Jenkinsfile)

`Jenkinsfile` 하나로 3가지 작업을 독립적으로 선택 실행.

### 파라미터

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `DB_CREATE` | false | prod 서버(192.168.0.61) GameDB 재생성 |
| `SERVER_BUILD` | true | Gradle 빌드 + Docker 이미지 빌드 & Push |
| `SERVER_RUN` | true | Ubuntu 서버에서 docker compose pull & restart |

### 조합 예시

| 조합 | 용도 |
|---|---|
| DB_CREATE + SERVER_RUN | DB 초기화 후 서버 재시작 |
| SERVER_BUILD + SERVER_RUN | 평상시 배포 |
| DB_CREATE + SERVER_BUILD + SERVER_RUN | 완전 초기화 후 풀 배포 |
| SERVER_RUN 만 | 서버 크래시 복구, 컨테이너 재기동 |

### DB_CREATE 단계 상세 흐름

1. `docker compose stop sbs-app` + `docker compose ps` 로 중지 확인
2. DB DROP + CREATE
3. schema.sql 전송 및 실행
4. `redis-cli FLUSHALL`
5. 서버는 중지 상태로 종료 → SERVER_RUN 으로 재시작 필요

### Redis 동기화

서버 기동 시 자동으로 DB → Redis 재구축됨 (`@Order(2)`, `@Order(3)`).
DB_CREATE 후 SERVER_RUN 하면 Redis도 자동으로 초기화됨.
DB_CREATE 단독 실행 시에도 FLUSHALL 로 Redis를 미리 비워두므로 불일치 없음.
