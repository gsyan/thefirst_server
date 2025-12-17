# Fleet Management SETTING Documentation

## 개요
함대, 함선, 모듈 에대한 설정

## 함대

## 함선


## 모듈
- ModuleBody
  선택시 자동으로 해당 모듈의 manage panel 보이고
  하위 모듈 scroll view로 표시, 비어있어도 module slot으로 체크해서 표시
  module type default = weapon   
  scrollview 에서 아이템 선택시 비어 있다면 add, 채워져 있다면 change 와 upgrade 버튼
  이 선택에 의해서 해당 모듈이 시각적으로도 선택됨이 반영됨
  ( 반대로 특정 모듈을 마우스 클릭으로 피킹해도 ui에 반영되어야 함 )
  
  add 나 change 및 upgrade 시 고려 사항
  1. TechLevel
  2. Mineral
  
  sort of weapon
  1. beam( techLevel 1 )
  2. missile( techLevel 3, Mineral 100 )
  3. hanger( techLevel 5, 
  
  
  - upgrade ( 선택된 ModuleBody 자체의 업그레이드 )
  - change ( 선택된 ModuleBody 자체의 업그레이드 )
  - add weapon
  - 
  


## 주요 기능
- 함대 생성/수정/삭제/조회
- 함대 활성화 관리
- 함대 데이터 Import/Export
- 함선별 모듈 관리 (Body, Weapon, Engine)

## 데이터 구조
```
Fleet (함대)
├── Ship 1 (함선 1)
│   ├── Body Module (본체 모듈) - 1개
│   ├── Weapon Modules (무기 모듈들) - 여러 개
│   └── Engine Module (엔진 모듈) - 1개
├── Ship 2 (함선 2)
│   └── ...
└── Ship N (함선 N)
```

## API 엔드포인트

### 1. 함대 목록 조회
```
GET /api/fleet/list
```
사용자의 모든 함대 목록을 조회합니다.

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "accountId": 1,
      "fleetName": "Main Battle Fleet",
      "description": "주력 전투 함대",
      "active": true,
      "dateTime": "2025-01-01T10:00:00",
      "lastModified": "2025-01-01T10:00:00"
    }
  ],
  "message": "함대 목록을 성공적으로 조회했습니다."
}
```

### 2. 활성 함대 조회
```
GET /api/fleet/active
```
현재 활성화된 함대의 상세 정보를 조회합니다.

### 3. 함대 상세 조회
```
GET /api/fleet/{fleetId}
```
특정 함대의 상세 정보(함선, 모듈 포함)를 조회합니다.

### 4. 함대 생성
```
POST /api/fleet/create
```
**Request Body:**
```json
{
  "fleetName": "New Fleet",
  "description": "새로운 함대"
}
```

### 5. 함대 활성화
```
POST /api/fleet/{fleetId}/activate
```
특정 함대를 활성화합니다. 기존 활성 함대는 자동으로 비활성화됩니다.

### 6. 함대 데이터 내보내기 (Export)
```
GET /api/fleet/{fleetId}/export
```
클라이언트로 전송할 수 있는 형태로 함대 데이터를 내보냅니다.

**Response:**
```json
{
  "success": true,
  "data": {
    "fleetName": "Main Battle Fleet",
    "description": "주력 전투 함대",
    "active": true,
    "ships": [
      {
        "shipName": "Flagship",
        "positionIndex": 0,
        "description": "기함",
        "modules": [
          {
            "moduleType": "Body",
            "moduleLevel": 5,
            "slotIndex": 0,
            "health": 1000.0,
            "attackFireCount": 0,
            "attackPower": 0,
            "attackCoolTime": 0,
            "movementSpeed": 50.0,
            "rotationSpeed": 30.0,
            "cargoCapacity": 500.0,
            "upgradeMoneyCost": 5000,
            "upgradeMaterialCost": 100
          }
        ]
      }
    ]
  }
}
```

### 7. 함대 데이터 가져오기 (Import) - 새 함대 생성
```
POST /api/fleet/import
```
**Request Body:**
```json
{
  "fleetName": "Imported Fleet",
  "description": "클라이언트에서 가져온 함대",
  "active": false,
  "ships": [
    {
      "shipName": "New Ship",
      "positionIndex": 0,
      "description": "새로운 함선",
      "modules": [
        {
          "moduleType": "Body",
          "moduleLevel": 3,
          "slotIndex": 0,
          "health": 600.0,
          "attackFireCount": 0,
          "attackPower": 0,
          "attackCoolTime": 0,
          "movementSpeed": 60.0,
          "rotationSpeed": 40.0,
          "cargoCapacity": 200.0,
          "upgradeMoneyCost": 3000,
          "upgradeMaterialCost": 60
        }
      ]
    }
  ]
}
```

### 8. 함대 데이터 업데이트 (Import) - 기존 함대 수정
```
PUT /api/fleet/{fleetId}/import
```
기존 함대의 데이터를 완전히 새로운 데이터로 교체합니다.

### 9. 함대 삭제
```
DELETE /api/fleet/{fleetId}
```
함대를 삭제합니다 (soft delete).

## 모듈 타입
- `Body` (0): 함선의 본체 모듈
- `Weapon` (1): 무기 모듈
- `Engine` (2): 엔진 모듈

## 인증
모든 API 요청에는 JWT 토큰이 필요합니다.
```
Authorization: Bearer {your_jwt_token}
```

## 클라이언트 연동 예시

### 1. 클라이언트에서 함대 데이터 저장
```javascript
// 클라이언트에서 함대 데이터를 서버에 저장
const fleetData = {
  fleetName: "My Custom Fleet",
  description: "클라이언트에서 만든 함대",
  active: true,
  ships: [/* 함선 데이터 */]
};

fetch('/api/fleet/import', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  },
  body: JSON.stringify(fleetData)
});
```

### 2. 서버에서 함대 데이터 불러오기
```javascript
// 서버에서 함대 데이터를 클라이언트로 가져오기
fetch('/api/fleet/1/export', {
  headers: {
    'Authorization': 'Bearer ' + token
  }
})
.then(response => response.json())
.then(data => {
  // 클라이언트에서 함대 데이터 사용
  const fleetData = data.data;
  // 게임 로직에 적용
});
```

## 데이터베이스 스키마
`fleet_schema.sql` 파일을 실행하여 필요한 테이블들을 생성할 수 있습니다.
`sample_fleet_data.sql` 파일을 실행하여 테스트용 샘플 데이터를 삽입할 수 있습니다.

## 주의사항
- 함대명은 사용자별로 고유해야 합니다.
- 활성 함대는 사용자당 하나만 가질 수 있습니다.
- 함선의 position_index는 함대 내에서 고유해야 합니다.
- 모듈의 slot_index는 함선 내에서 고유해야 합니다.
- 모든 삭제는 soft delete로 처리됩니다.
