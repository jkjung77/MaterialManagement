# 자재관리 앱 — 서버 API 항목 명세

대상: 회사 서버 개발자 (이후 이 저장소에서 서버 구축 예정)  
클라이언트: Android 앱 `kr.baraplt.material`  
목적: **공장마다** 같은 공장 ID를 쓰는 여러 명이 입고·생산·폐기를 넣고, 재고 정본을 서버에 둔다.  
현장: 통신이 약함 → 오프라인 입력 후 통신될 때 동기화. 담당자는 **휴대폰 통신사 데이터**로 접속한다. 사내망·IP 화이트리스트만으로는 운영하지 않는다.

앱(2026-09-14 이후): 공장 ID + 공장 암호로 입장. 공장마다 로컬 DB가 갈린다. 서버가 생기면 같은 ID·암호로 묶는다.

---

## 1. 전제와 범위

### 1.1 하는 일
- 자재 마스터, 단품(BOM/US), 완제품 구성, 월계획
- 입고 / 반출 / 폐기 / 재고조사 조정
- 단품 일 생산실적 → 자재 투입 자동 계산
- 월 마감 (현재고를 다음 달 시작재고로 이관)
- 권한: `MANAGER`(기초정보·마감·재고조사 승인) / `STAFF`(입고·생산·폐기)

### 1.2 하지 않는 일 (1차)
- 바코드, 발주서, 출하 SCM, 완제품 일 판매실적
- Firebase 등 외부 BaaS. 정본은 **회사 서버(자체 호스팅 Supabase/PostgreSQL)** 만.

### 1.3 규모
- 공장(워크스페이스) 여러 개. 예: `경주1공장`, `경주2공장`, `성남공장`
- 공장당 자재 번호 1~500, 단품 1~500(BOM 최대 30), 완제품 1~26(구성 최대 15)
- 공장당 동시 사용자 약 3명 (관리책임자 1 + 담당)
- 공장 간 데이터는 **완전히 분리**. 다른 공장 ID로는 조회·수정 불가.

### 1.4 정본
**서버가 정본.** 폰은 캐시 + 미전송 큐.  
재고 수식은 서버와 앱이 같아야 한다.

```
현재고 = 시작재고 + 입고 − 반출 − 폐기 + 조정 − 생산투입

생산투입(자재) = Σ (그 달 단품 일 생산수량 × BOM US)
입고금액 = Σ (입고수량 × 입고시점 단가)
사용금액 = 생산투입 × 현재 자재단가
실패비용 = 폐기수량 × 단가
단품자재비 = Σ (US × 자재단가)
자재비율 = 단품자재비 / 단품판매단가
```

마감된 달(`yearMonth`가 close 됨)은 STAFF가 실적·입출고를 수정할 수 없다. MANAGER만 마감 해제 가능.

---

## 2. 공통 규약

| 항목 | 값 |
|---|---|
| 프로토콜 | HTTPS, JSON, UTF-8. 앱은 HTTPS만. PostgreSQL 5432는 외부 비공개 |
| 인증 | `Authorization: Bearer {accessToken}` — 토큰에 **공장(workspace)** 범위가 들어간다 |
| 공장 ID | 문자열. trim. 1~32자. 예: `경주1공장`. 대소문자·공백까지 포함해 **정규화 후 동일하면 같은 공장** |
| 날짜 | `YYYY-MM-DD` (예: `2026-08-13`) |
| 년월 | `YYYY-MM` (예: `2026-08`) |
| 시각 | Unix epoch **밀리초** (`updatedAt`, `createdAt`, `closedAt`) |
| 수량 | 소수 허용 (원단 m, 수지 kg). 생산수량은 정수 |
| 금액 | 원, 소수 가능. 표시는 반올림 |
| 행 ID | 서버가 발급하는 정수. 앱 로컬 PK와 다름. 동기화 시 `serverId` |
| 테넌트 | 모든 업무 테이블에 `workspaceId`. unique는 **공장 안**에서만 (예: 자재 codeNo는 공장당 unique) |
| 삭제 | 물리 삭제 대신 `isActive=false` (마스터). 이력(입출고·생산)은 마감 전이면 삭제 API |
| 페이지 | 목록은 `limit`(기본 200) / `offset` 또는 `updatedSince` |
| 오류 | 아래 4.9 |

성공 응답 껍데기 (권장):

```json
{
  "ok": true,
  "serverTime": 1773123456789,
  "data": {}
}
```

실패:

```json
{
  "ok": false,
  "code": "MONTH_CLOSED",
  "message": "마감된 달입니다"
}
```

공개 서브도메인(희망 `https://material.jayoo.kr`)으로 앱이 붙는다. CORS는 앱만 쓰면 불필요. IP 고정이 불가하므로 방화벽에서 앱 HTTPS만 열고 DB 포트는 막는다.

---

## 3. 권한과 공장 입장

두 겹이다. **공장 암호**와 **역할(PIN/계정)** 을 섞지 않는다.

| 구분 | 앱(현재) | 서버 |
|---|---|---|
| 공장 입장 | 공장 ID + 공장 암호 (4~32자). 같은 공장 사람이 공유 | `Workspace.passwordHash`. 로그인 성공 시 그 공장 토큰 |
| 역할 | 단말 PIN. `MANAGER` / `STAFF` | 같은 공장 안의 User.role |

| 역할 | 할 수 있는 것 (그 공장 데이터만) |
|---|---|
| `STAFF` | 입고, 반출, 폐기, 단품 일 생산, 조회, 동기화 |
| `MANAGER` | STAFF 전부 + 마스터·시작재고·재고조사 승인·마감·그 공장 사용자 |

1차 로그인(앱과 맞춤):

```json
{ "workspaceId": "경주1공장", "password": "공장암호", "staffName": "담당자" }
```

- 공장 ID·암호가 맞으면 토큰 발급. `staffName`은 이력 `createdBy` 표시용.
- 역할은 서버 User가 있으면 그 값, 없으면 기본 `STAFF`. MANAGER 승격은 공장 관리자/PIN.
- **공장 ID만으로 입장 불가.** 암호 없으면 `WORKSPACE_AUTH`.
- 다른 공장 토큰으로 경주1공장 데이터를 읽으면 `FORBIDDEN`.

2차: 공장마다 loginId/비밀번호를 따로 둘 수 있다. 그래도 모든 행은 `workspaceId`로 격리한다.

---

## 4. 리소스(테이블) 항목

서버 PK는 `id`. 앱은 동기화 후 `serverId`를 보관한다.  
아래 업무 테이블은 모두 **`workspaceId`를 가진다.** unique는 `(workspaceId, …)` 기준.

### 4.0 Workspace (공장)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | long | 서버 PK |
| code | string | 공장 ID. 앱과 동일. 1~32자, trim 후 unique. 예: `경주1공장` |
| name | string | 표시명. 없으면 `code`와 같음 |
| passwordHash | (서버만) | 공장 공유 암호. 앱은 SHA-256(`material|{code}|{암호}`)를 쓰지만 **서버는 bcrypt/argon2를 권장**. 앱 해시를 그대로 쓰지 말 것 |
| isActive | bool | false면 입장 거부 |
| createdAt | long | |

앱 로컬 DB 파일명: `material_{code}.db` (파일에 못 쓰는 글자는 `_`). 서버는 파일이 아니라 `workspaceId` 컬럼으로 나눈다.

### 4.1 User
| 필드 | 타입 | 설명 |
|---|---|---|
| id | long | |
| workspaceId | long | 소속 공장 |
| loginId | string | 선택. 1차는 비워도 됨 |
| name | string | 표시 이름 (정길모, 담당자) |
| role | `MANAGER` \| `STAFF` | |
| isActive | bool | |
| passwordHash | (서버만) | 개인 계정용. 공장 암호와 별개 |

### 4.2 Material (자재)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| workspaceId | long | |
| codeNo | int | 1~500, unique **(workspaceId + codeNo)** |
| name | string | 자재명(품목) |
| unit | string | `ea` / `kg` / `m` / `roll` 등 |
| packUnit | string | 선택. 예: `500(포대)` |
| unitPrice | number | 단위당 단가(원) |
| safetyStock | number | 안전재고. 0이면 경보 없음 |
| leadTimeDays | int | 발주 리드타임(일) |
| note | string | |
| isActive | bool | |
| updatedAt | long | |
| updatedBy | long | **서버 권장.** 현재 앱 마스터에는 없음 |

### 4.3 OpeningStock (월 시작재고)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| workspaceId | long | |
| materialId | long | |
| yearMonth | string | unique (workspaceId + materialId + yearMonth) |
| qty | number | |

마감 시 서버가 다음 달 row를 현재고로 upsert.

### 4.4 StockMovement (입출고 이력)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | long | |
| workspaceId | long | |
| clientUid | string | **서버 신규.** 앱 UUID. unique는 공장 안 또는 전역 |
| materialId | long | |
| type | string | `INBOUND` 입고 / `OUTBOUND` 반출 / `SCRAP` 폐기 / `ADJUST` 재고조정 |
| qty | number | ADJUST만 음수 가능 (실사 < 장부) |
| unitPrice | number | 발생 시점 단가 스냅샷 |
| occurredOn | date | |
| note | string | |
| createdAt | long | |
| createdBy | long | user id. 현재 앱 JSON은 문자열(이름) — 서버는 id로 통일 |

`ADJUST`는 MANAGER만. 재고조사 일괄 API로 넣는 것을 권장.

### 4.5 Product (단품)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| workspaceId | long | |
| codeNo | int | 1~500 unique (workspaceId + codeNo) |
| name | string | FRT, CTR, STEP1 … |
| sellPrice | number | 단품판매단가 |
| isActive | bool | |
| updatedAt | long | |

### 4.6 ProductBom
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| workspaceId | long | |
| productId | long | |
| materialId | long | unique (productId + materialId) |
| usQty | number | 대당 사용량 US. 0 초과 |
| sortOrder | int | 0~29, 최대 30행 |

US는 월평균 중량 체크값(현장 입력). 매일 바뀌지 않고 마스터로 관리.

### 4.7 DailyProduction (단품 일 실적)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| workspaceId | long | |
| clientUid | string | **서버 신규.** UUID unique |
| productId | long | unique (workspaceId + productId + workDate) |
| workDate | date | |
| qty | int | 0이면 삭제와 동일 |
| updatedAt | long | **서버 권장.** 현재 앱에는 없음 |
| updatedBy | long | **서버 권장** |

같은 날·같은 단품은 **마지막 값이 정본**(덮어쓰기).

### 4.8 ProductPlan (단품 월 계획)
| 필드 | 타입 | |
|---|---|---|
| workspaceId | long | |
| productId | long | unique (workspaceId + productId + yearMonth) |
| yearMonth | string | |
| qty | int | |

### 4.9 FinishedGood (완제품)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| workspaceId | long | |
| codeNo | int | 1~26 unique (workspaceId + codeNo) |
| name | string | A1SPK 등 |
| sellPrice | number | |
| isActive | bool | |

### 4.10 FinishedComposition
| 필드 | 타입 | |
|---|---|---|
| workspaceId | long | |
| finishedGoodId | long | |
| productId | long | unique pair, 최대 15 |
| sortOrder | int | |

수량 1 고정(현재 앱). 나중에 필요하면 `qty` 추가.

### 4.11 MonthlyPlan (완제품 월 계획)
| 필드 | 타입 | |
|---|---|---|
| workspaceId | long | |
| finishedGoodId | long | unique (workspaceId + finishedGoodId + yearMonth) |
| yearMonth | string | |
| qty | int | |

완제품 **일 판매실적은 없음.**

### 4.12 MonthClose
| 필드 | 타입 | |
|---|---|---|
| workspaceId | long | PK와 함께 |
| yearMonth | string | unique (workspaceId + yearMonth) |
| closedAt | long | |
| closedBy | long | user id. 현재 앱 JSON은 문자열 |

---

## 5. API 목록

Base path 예: `https://material.jayoo.kr/api/v1` (도메인 확정 전 가칭)

모든 업무 API는 토큰의 `workspaceId`만 본다. URL에 공장을 넣지 않아도 된다.

### 5.1 인증

#### `POST /auth/login`
```json
{
  "workspaceId": "경주1공장",
  "password": "공장공유암호",
  "staffName": "담당자"
}
```
응답:
```json
{
  "accessToken": "...",
  "expiresIn": 86400,
  "refreshToken": "...",
  "workspace": { "id": 10, "code": "경주1공장" },
  "user": { "id": 1, "name": "담당자", "role": "STAFF" }
}
```

#### `POST /workspaces` (MANAGER 또는 초기 부트스트랩)
공장 생성. body: `{ "code", "password", "name?" }`  
이미 있는 code → `409 DUPLICATE_WORKSPACE`.

#### `PUT /workspaces/me/password` (그 공장 MANAGER)
공장 암호 변경. 기존 토큰은 무효화하는 것을 권장.

#### `POST /auth/refresh`
#### `POST /auth/logout`

토큰 만료되어도 **미전송 큐는 폰에 남긴다.** 재로그인 후 재전송.

### 5.2 동기화 (앱이 주로 호출)

오프라인 대비 **이 두 개만 있어도 1차는 된다.**

#### `GET /sync/snapshot?yearMonth=2026-08`
해당 달 화면을 그리는 데 필요한 전부.

응답 `data`:
```json
{
  "workspace": { "id": 10, "code": "경주1공장" },
  "yearMonth": "2026-08",
  "closed": false,
  "serverTime": 1773123456789,
  "users": [ { "id": 1, "name": "정길모", "role": "MANAGER" } ],
  "materials": [ ],
  "openings": [ ],
  "movements": [ ],
  "products": [ ],
  "bom": [ ],
  "production": [ ],
  "productPlans": [ ],
  "finished": [ ],
  "composition": [ ],
  "monthlyPlans": [ ],
  "closes": [ ]
}
```

위 키 이름은 앱 백업 JSON(`BackupBundle`)과 같다. 초기 이관은 앱 **설정 → 백업 내보내기** 파일을 그대로 import 해도 된다. (`version: 1`, `clientUid` 없음)

마스터(materials 등)는 달에 무관하게 최신본을 같이 내려도 된다.

#### `GET /sync/changes?since={millis}`
`updatedAt > since` 인 변경만. 트래픽 절약.

#### `POST /sync/push`
폰에 쌓인 미전송을 한 번에 올린다. **같은 `clientUid`는 멱등(한 번만 반영).**

```json
{
  "deviceId": "android-xxxxxxxx",
  "workspaceId": "경주1공장",
  "movements": [
    {
      "clientUid": "550e8400-e29b-41d4-a716-446655440000",
      "materialId": 2,
      "type": "INBOUND",
      "qty": 1000,
      "unitPrice": 2300,
      "occurredOn": "2026-08-10",
      "note": "8월 입고"
    }
  ],
  "production": [
    {
      "clientUid": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "productId": 1,
      "workDate": "2026-08-01",
      "qty": 80
    }
  ]
}
```

응답:
```json
{
  "accepted": [
    { "clientUid": "...", "serverId": 101 }
  ],
  "rejected": [
    { "clientUid": "...", "code": "MONTH_CLOSED", "message": "마감된 달" }
  ]
}
```

마감 달·권한 없음·없는 ID는 `rejected`에 넣고, 나머지는 성공. **부분 성공 허용.**

### 5.3 마스터 (MANAGER)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/materials` | 목록 |
| POST | `/materials` | 등록. body는 4.2 (id 제외) |
| PUT | `/materials/{id}` | 수정 |
| GET/POST/PUT | `/products` | 단품 |
| PUT | `/products/{id}/bom` | BOM 전체 교체. 배열 최대 30 |
| GET/POST/PUT | `/finished-goods` | 완제품 |
| PUT | `/finished-goods/{id}/composition` | 단품 id 배열 최대 15 |

같은 공장 안 `codeNo` 중복 → `409 DUPLICATE_CODE`. 다른 공장은 같은 번호 허용.

### 5.4 시작재고 · 계획 (MANAGER)

| 메서드 | 경로 |
|---|---|
| PUT | `/openings` body: `{ "materialId", "yearMonth", "qty" }` |
| PUT | `/product-plans` `{ "productId", "yearMonth", "qty" }` |
| PUT | `/monthly-plans` `{ "finishedGoodId", "yearMonth", "qty" }` |

### 5.5 입출고 (STAFF, ADJUST는 MANAGER)

개별 호출도 제공. 앱은 가능하면 `/sync/push`만 써도 됨.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/movements` | 입고/반출/폐기/조정 |
| DELETE | `/movements/{id}` | 미마감만 |
| GET | `/movements?yearMonth=2026-08&materialId=` | 이력 |

### 5.6 생산 (STAFF)

| 메서드 | 경로 |
|---|---|
| PUT | `/production` `{ "productId", "workDate", "qty" }` qty=0이면 삭제 |
| GET | `/production?yearMonth=2026-08` |

### 5.7 재고조사 (MANAGER)

`POST /stocktake`

```json
{
  "yearMonth": "2026-08",
  "occurredOn": "2026-08-31",
  "counts": [
    { "materialId": 1, "physicalQty": 90 }
  ]
}
```

서버: 현재고와 차이를 계산해 `ADJUST` 이력을 만든다. 차이 0은 무시.

### 5.8 마감 (MANAGER)

`POST /months/{yearMonth}/close`  
해당 달 현재고를 계산해 **다음 달 OpeningStock**을 upsert하고 MonthClose 기록.

`DELETE /months/{yearMonth}/close`  
마감 해제. 다음 달 시작재고는 삭제하지 않거나, 정책으로 “다시 마감 시 덮어씀”을 명시.

### 5.9 조회(계산 결과)

앱이 다시 계산해도 되지만, 서버와 숫자를 맞추려면 제공.

`GET /reports/{yearMonth}`

앱 `MonthReport` + 자재/단품 스냅샷과 동일해야 한다.

```json
{
  "yearMonth": "2026-08",
  "salesAmount": 38975000,
  "usageAmount": 26992850,
  "purchaseAmount": 2357500,
  "scrapCost": 4600,
  "producedQty": 1675,
  "inboundQty": 1000,
  "materialRatio": 0.6926,
  "grade": "보통",
  "materials": [
    {
      "materialId": 1,
      "codeNo": 1,
      "opening": 900,
      "inbound": 0,
      "outbound": 0,
      "scrap": 0,
      "adjust": 0,
      "usage": 812,
      "current": 88,
      "purchaseAmount": 0,
      "usageAmount": 24360000,
      "scrapCost": 0,
      "status": "LOW",
      "daysCover": 3.3
    }
  ],
  "products": [
    {
      "productId": 1,
      "produced": 400,
      "monthPlan": 0,
      "materialCost": 19415,
      "materialRatio": 0.7766,
      "usageAmount": 7766000,
      "salesAmount": 10000000
    }
  ]
}
```

- `materialRatio`(월) = `usageAmount / salesAmount`
- `grade`: 비율 ≤0.55 좋음, ≤0.70 보통, 그 외 주의. 매출 0이면 `-`
- `status`: `OK` | `LOW`(현재고 ≤ 안전재고, 안전>0) | `CRITICAL`(현재고 ≤ 0)
- `daysCover`: 현재고 ÷ (당월 사용량 ÷ 그 달 일수). 사용 0이면 null

`GET /reports/{yearMonth}/shortage`  
완제품 월계획 → 단품 전개 → 자재 소요 − 현재고. 부족 목록(발주 참고).

### 5.10 사용자 (MANAGER)

`GET/POST/PUT /users`  
비밀번호 재설정. 1차는 관리자가 콘솔/API로 생성.

---

## 6. 오류 코드

| code | HTTP | 의미 |
|---|---|---|
| UNAUTHORIZED | 401 | 토큰 없음/만료 |
| WORKSPACE_AUTH | 401 | 공장 ID 또는 공장 암호 불일치 |
| WORKSPACE_INACTIVE | 403 | 공장 사용 중지 |
| FORBIDDEN | 403 | 역할 부족이거나 **다른 공장** 데이터 |
| MONTH_CLOSED | 409 | 마감된 달 수정 |
| DUPLICATE_CODE | 409 | 그 공장에서 codeNo 중복 |
| DUPLICATE_WORKSPACE | 409 | 공장 ID(code) 이미 있음 |
| DUPLICATE_UID | 200 + accepted | clientUid 이미 처리(성공으로 봐도 됨) |
| NOT_FOUND | 404 | |
| VALIDATION | 400 | 번호 범위, BOM 30 초과 등 |
| CONFLICT | 409 | 동시에 마스터를 고침. `updatedAt` 비교 |

마스터 PUT은 `If-Unmodified-Since` 또는 body `updatedAt`이 서버보다 오래되면 `CONFLICT`. 앱은 snapshot을 다시 받는다.

입출고·생산은 **추가/덮어쓰기**라 충돌이 적다. 생산은 같은 날 마지막 qty가 이긴다.

---

## 7. 앱 동기화 동작 (서버가 알면 좋은 것)

1. 앱 첫 화면에서 공장 ID·암호 입력 → `POST /auth/login`.
2. 그 공장 로컬 DB만 연다. 공장을 바꾸면 다른 로컬 DB로 전환.
3. `GET /sync/snapshot` 또는 `/sync/changes` (토큰 공장만).
4. 오프라인 입력 → 폰 큐에 `clientUid` → 통신되면 `POST /sync/push`.
5. `MONTH_CLOSED` / `FORBIDDEN` / `WORKSPACE_AUTH`는 사용자에게 표시.
6. 앱에 “미전송 N건”.

푸시(FCM)는 1차 불필요. 당김(pull)만.  
엑셀 저장(`설정 → 엑셀로 저장`)은 **단말 기능**. 서버 import API는 1차 없음. JSON 백업도 공장 단위.

---

## 8. 1차 / 2차 나눔

**1차 (공장 분리 + 동시 사용)**  
`/auth/login`(공장 ID+암호), `/workspaces`, `/sync/snapshot`, `/sync/push`, `/months/{ym}/close`, `/stocktake`

**2차**  
`/sync/changes`, `/reports`, 공장별 사용자 계정, 감사 로그, 엑셀 서버 반입.

---

## 9. 샘플 숫자 (엑셀 APP과 동일, 검수용)

자재 1 원단(청색): 단위 m, 단가 30000, 8월 시작 900, 안전 80, LT 12일  
자재 2 수지(청색): kg, 2300, 시작 800, 입고 1000, 폐기 2, 안전 500  

단품 1 FRT 판매 25000, BOM:  
원단 0.6, 클립 2, 수지 0.55, 스크류 1, PAD 7 → 자재비 19415

8월 FRT 생산 400대이면 원단 투입 240 + CTR/REAR분 포함 시 원단 사용 812, 현재고 88.

이 숫자로 `GET /reports/2026-08`을 맞추면 앱과 골든 테스트가 끝난다.

---

## 10. 보안·운영

- HTTPS만. 희망 호스트 `material.jayoo.kr`. 5432는 외부 차단.
- 공장 암호·개인 암호는 서버에서 해시(bcrypt/argon2). 앱 로컬 SHA-256은 단말 확인용일 뿐 서버 저장 형식이 아님.
- 토큰에 workspace 범위. 쿼리마다 `WHERE workspace_id = token.workspace`.
- 휴대폰 회선 접속 → IP 화이트리스트 사용 금지.
- 감사: createdBy, closedBy. 가능하면 workspaceId 로그.
- 백업: DB 일일 + 공장 단위. 앱 JSON/xlsx는 비상·열람용.
- 개인정보: 이름·공장 ID. 위치·주소록 없음.
- 앱은 업데이트 확인용 인터넷 권한이 있다. Play 데이터 보안은 “업데이트·동기화 시 전송, 자재 데이터는 서버 연동 후에만”으로 맞출 것.

---

## 11. 앱 쪽에서 바로 줄 수 있는 것

- 이 명세
- 앱 백업 JSON (`설정 → 백업 내보내기`) — 필드명은 같음. `workspaceId`는 아직 JSON에 없음. import 시 요청한 공장에 넣으면 된다
- 엑셀 내보내기는 열람용. 서버 스키마 원본이 아님
- 패키지 `kr.baraplt.material`

서버 1차: **공장을 만들고**, JSON을 그 공장에 넣은 뒤, 같은 공장 토큰으로 snapshot을 돌려주면 앱 연동이 된다.
