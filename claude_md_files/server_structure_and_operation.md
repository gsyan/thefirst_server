# 서버 구조 및 작동 설명

## 레이어 구조

```
클라이언트 요청
    ↓
[JwtAuthenticationFilter]     토큰 검증, SecurityContext 설정
    ↓
[OnlineActivityInterceptor]   요청 전달 (preHandle 없음)
    ↓
[Controller]                  요청 파싱, characterId 추출, Service 호출
    ↓
[Service]                     비즈니스 로직, @Transactional
    ↓
[Repository]                  DB 접근 (JPA)
    ↓
응답 전송
    ↓
[OnlineActivityInterceptor.afterCompletion()]  lastOnlineAt 갱신 (30s 스로틀)
```

---

## 인증 흐름

### JWT 토큰 구조
- Subject: `accountId` (항상 포함)
- Claim `characterId`: 캐릭터 선택 후에만 포함
- Algorithm: HS512

### 토큰 발급 순서
```
1. POST /api/account/login
   → JWT(accountId only)

2. POST /api/character/select-character/{characterId}
   → JWT(accountId + characterId)  ← 게임 API 전부 이걸 사용

3. POST /api/account/refresh
   → RefreshToken → 새 AccessToken (characterId 유지)
```

### characterId 비트 마스킹
```
JWT 내 characterId = (worldId << 56) | actualCharacterId
서버 내부 사용:  actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL
```

---

## OnlineActivityInterceptor / Service / WebMvcConfig 세트

### 역할
모든 인증된 게임 API 호출 성공 시 `Character.lastOnlineAt`을 갱신.
자원 수확 시 온라인/오프라인 구간을 구분하는 기준값으로 사용됨.

### 흐름
```
WebMvcConfig
  → OnlineActivityInterceptor를 /api/** (단, /api/account/** 제외) 에 등록

OnlineActivityInterceptor.afterCompletion()
  → 응답 상태 2xx 확인
  → JWT에서 characterId 추출
  → OnlineActivityService.touch(characterId) 호출

OnlineActivityService.touch()
  → @Transactional(REQUIRES_NEW)  ← 요청 트랜잭션과 독립
  → CharacterRepository.updateLastOnlineAtIfStale()
     UPDATE Character SET lastOnlineAt = :now
     WHERE id = :id AND (lastOnlineAt IS NULL OR lastOnlineAt < :threshold)
     -- threshold = now - 30s → 30초 이내면 업데이트 안 함 (스로틀)
```

### REQUIRES_NEW를 쓰는 이유
`afterCompletion`은 서비스 메서드의 `@Transactional`이 이미 커밋된 후 실행됨.
기존 트랜잭션이 없으므로 독립적인 새 트랜잭션을 열어야 `@Modifying` 쿼리가 실행 가능.

---

## 온라인/오프라인 자원 적립 계산 (calcCreditedSeconds)

### 배경
- 온라인(포그라운드): 캡 없이 전량 적립
- 오프라인(백그라운드/종료): offlineCap 적용 (기본 3h, 기술레벨에 따라 최대 7h)

### 변수
- C = `collectDateTime` (마지막 수확 시각)
- L = `lastOnlineAt` (마지막 온라인 확인 시각, 하트비트 또는 API 호출로 갱신)
- N = `now` (현재 시각)
- grace = 60초 (하트비트 누락 여유시간)

### 계산 로직
```
L == null
  → 전 구간 오프라인 취급: min(N - C, offlineCap)

N - L ≤ grace(60s)
  → 현재 온라인 중: N - C (캡 없음)

N - L > grace
  → C→L 구간: 온라인, 전량 적립 (max(0, L - C))
  → L→N 구간: 오프라인, min(N - L, offlineCap)
  → 합산 반환
```

### offlineCap 계산 (calcOfflineCapSeconds)
```java
capHours = 3 + (maxTechLevel / 2)
// 기술레벨 0/1 → 3h, 2/3 → 4h, 4/5 → 5h, 6/7 → 6h, 8 → 7h
```

### 수확 전 하트비트 선발송 (클라이언트)
"미네랄 수확" 버튼 클릭 시 하트비트를 먼저 보내고 collect 호출.
네트워크 불안정으로 하트비트가 누락된 구간을 보정하기 위함.

---

## 컨트롤러 / 엔드포인트 목록

### AccountController `/api/account` (인증 불필요)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/signup` | 이메일/비밀번호 가입 |
| POST | `/login` | 로그인 |
| POST | `/refresh` | AccessToken 재발급 |
| POST | `/google-login` | Google ID Token 로그인 |
| POST | `/guest-login` | Guest ID 로그인 |
| POST | `/link-google` | Google 계정 연동 |
| POST | `/unlink-google` | Google 연동 해제 |

### CharacterController `/api/character`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/create` | 캐릭터 생성 |
| POST | `/select-character/{id}` | 캐릭터 선택 → characterId 포함 JWT 재발급 |
| GET | `/characters` | 내 캐릭터 목록 |
| POST | `/validate-name` | 이름 중복/비속어 검사 |
| POST | `/rename` | 이름 변경 (횟수 제한 2회) |

### FleetController `/api/fleet`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/list` | 함대 목록 |
| GET | `/{fleetId}` | 함대 상세 |
| GET | `/active` | 활성 함대 |
| POST | `/{fleetId}/activate` | 함대 활성화 |
| DELETE | `/{fleetId}` | 함대 삭제 (soft delete) |
| POST | `/add-ship` | 함선 추가 (자원 소비) |
| POST | `/upgrade-module` | 모듈 레벨업 |
| POST | `/change-module` | 모듈 교체 (상위 모듈로) |
| POST | `/unlock-module` | 모듈 슬롯 해금 |
| POST | `/research-tech-level` | 기술레벨 연구 |
| POST | `/change-formation` | 편대 변경 |

### ZoneController `/api/zone`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/clear` | 존 클리어 |
| POST | `/collect` | 자원 수확 |
| POST | `/kill` | 킬 보상 |
| POST | `/heartbeat` | 온라인 시간 갱신 |

### PvpController `/api/pvp`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/list` | 매칭 상대 목록 |
| POST | `/refresh` | 상대 재매칭 |
| POST | `/battle/start` | 전투 시작 (battleToken 발급) |
| POST | `/battle/result` | 전투 결과 보고 |

### RankingController `/api/ranking`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/pvp` | PvP 랭킹 페이징 |
| POST | `/pvp/my-rank` | 내 PvP 랭킹 |
| POST | `/zone` | Zone 랭킹 페이징 |

### ProgressController `/api/progress`
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/save` | 진행도 저장 |
| GET | `/{category}` | 카테고리별 진행도 조회 |

---

## Entity / DB 테이블 구조

| Entity | 주요 필드 | 특징 |
|--------|----------|------|
| Account | id, email(UK), password(bcrypt), googleId(UK), deleted | Guest/Google/Email 3방식 |
| Character | id, accountId, mineral×4 + fraction×4, collectDateTime, lastOnlineAt, nameChangeCount=2, deleted | 소수점 자원 누적 |
| Fleet | id, characterId, isActive, formation, deleted | 캐릭터당 다중 함대 |
| Ship | id, fleetId, positionIndex, deleted | 함대 내 순서 |
| ShipModule | id, shipId, moduleType, moduleSubType, moduleLevel, bodyIndex, slotIndex, deleted | 장착 위치 |
| ShipModuleLevel | id, shipId, bodyIndex, moduleType, slotIndex, moduleSubType, level | 해금 이력 + 레벨 |
| ModuleResearch | id, characterId, moduleType, moduleSubType, researchId(tech_level_N), researched | 연구 상태 |
| ClearedZone | id, characterId, zoneName(UK+characterId), clearedAt | 존 클리어 이력 |
| PvpRecord | id, characterId(UK), score=1000, wins, losses | PvP DB 백업 |
| Progress | id, characterId, category, progressKey(UK+category) | 업적/진행도 |

**공통 패턴**: 모든 주요 Entity는 `deleted` 필드로 soft delete.

---

## 동시성 제어

### 비관적 락
```java
// 자원 차감 전 항상 사용
characterRepository.findByIdForUpdate(characterId)
// → SELECT ... FOR UPDATE → 동시 요청 순차 처리 보장
```

### @Transactional 원자성
함선 추가, 모듈 업그레이드, 자원 수확 등 자원 변경 작업은 모두 단일 트랜잭션.
중간 실패 시 전체 롤백.

---

## Redis 구조 (PvP + 랭킹)

```
pvp:ranking          ZSET   실시간 PvP 점수 (배틀 직후 즉시 반영)
pvp:ranking:snapshot ZSET   랭킹 보드 표시용 (60분 주기 동기화)
pvp:info:{charId}    Hash   개인 배틀 정보 (score, refreshCount 등)
pvp:list:{charId}    List   매칭 상대 목록
pvp:battle:{token}   String 배틀 토큰 (TTL 제한)
zone:ranking         ZSET   실시간 Zone 점수 (chapter*1000 + stage)
zone:ranking:snapshot ZSET  Zone 랭킹 보드용 (60분 주기)
rank:name            Hash   characterId → characterName 매핑
```

---

## 게임 데이터 로딩 (GameDataService)

서버 시작 시 JSON 파일로부터 게임 데이터를 메모리에 로드:
- `DataTableConfig.json`: 전역 설정 (함선 추가 비용, 모듈 해금 비용 등)
- `DataTableModule.json`: 모듈 능력치/비용/슬롯 정보
- `DataTableResearch.json`: 연구 비용 (tech_level_N, 모듈 추가 비용)
- `DataTableZone.json`: 존별 수확량/킬 보상

클라이언트의 Unity DataTable과 동기화 필요 (generator 툴 사용).

---

## 자원 수확 전체 흐름

```
POST /api/zone/collect
  → collectZone(characterId)
    → findByIdForUpdate (비관적 락)
    → calcOfflineCapSeconds (기술레벨 기반)
    → collectZoneResources
        → calcCreditedSeconds(C, L, N, offlineCap)
           온라인 구간(C→L) + 오프라인 구간(L→N, 캡 적용)
        → 클리어된 존 전체 rate 합산
        → rate × creditedSeconds → 자원 계산
        → fraction 누적 (소수점 손실 방지)
    → collectDateTime = now
    → save
  → response: collectDateTime, elapsedSeconds(실제 적립 시간), rewardInfo
```

---

## 에러 처리

모든 비즈니스 예외는 `BusinessException(ServerErrorCode)` → `GlobalExceptionHandler`에서 클라이언트에 errorCode 전달.

주요 에러 분류:
- `*_FAIL_INSUFFICIENT_MINERAL*`: 자원 부족
- `*_FAIL_INSUFFICIENT_TECH_LEVEL`: 기술레벨 부족
- `*_FAIL_CHARACTER_NOT_FOUND`: 캐릭터 없음
- `*_FAIL_INVALID_TOKEN`: 토큰 문제
- `*_FAIL_MODULE_LEVEL_MISMATCH`: 모듈 레벨 불일치

---

## 주의사항 / 알려진 제약

- `FleetService.java` 1100줄+ → 가장 복잡한 파일, 수정 시 주의
- `CHARACTER_NOT_FOUND`는 여러 서비스에서 공용 → 에러 코드 중복 가능성
- 랭킹 스케줄 주기는 `application.properties`에서 설정 (`ranking.pvp.sync.rate-minutes`)
- Firebase Auth 사용 여부는 `google.use-firebase-auth` 설정으로 제어
- 월정액 구독(24h offlineCap) 미구현 → `calcOfflineCapSeconds` 수정 필요
