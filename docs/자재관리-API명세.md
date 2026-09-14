# 자재관리 앱 — 서버 API 항목 명세

대상: 회사 서버 개발자  
클라이언트: Android 앱 `kr.baraplt.material` (현재는 단말 Room DB만 사용)  
목적: 관리책임자 1명 + 담당자 최대 3명이 **동시에** 입고·생산·폐기를 넣고, 재고 정본을 서버에 둔다.  
현장: 통신·와이파이가 약함 → **오프라인 입력 후 통신 될 때 동기화**가 필수.

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
- 외부 클라우드(Firebase 등) — 단가·재고는 사내만

### 1.3 규모
- 자재 번호 1~500 (현재 약 300)
- 단품 1~500 (현재 약 370), 단품당 투입자재 최대 30
- 완제품 1~26, 완제품당 구성 단품 최대 15
- 동시 사용자 약 3명

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
| 프로토콜 | HTTPS, JSON, UTF-8 |
| 인증 | `Authorization: Bearer {accessToken}` |
| 날짜 | `YYYY-MM-DD` (예: `2026-08-13`) |
| 년월 | `YYYY-MM` (예: `2026-08`) |
| 시각 | Unix epoch **밀리초** (`updatedAt`, `createdAt`, `closedAt`) |
| 수량 | 소수 허용 (원단 m, 수지 kg). 생산수량은 정수 |
| 금액 | 원, 소수 가능. 표시는 반올림 |
| ID | 서버가 발급하는 정수. 앱 로컬 ID와 다름. 동기화 시 `serverId` 사용 |
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

사내망 또는 VPN. CORS는 앱만 쓰면 불필요.

---

## 3. 권한

| 역할 | 할 수 있는 것 |
|---|---|
| `STAFF` | 입고, 반출, 폐기, 단품 일 생산 입력/수정(미마감 달), 조회, 동기화 |
| `MANAGER` | STAFF 전부 + 자재/단품/완제품/BOM/계획 등록·수정, 시작재고, 재고조사 승인, 월 마감/해제, 사용자 관리 |

계정은 서버가 만든다. 1차 계정 예: 관리책임자 1, 생산/자재/출하 담당 각 1.

---

## 4. 리소스(테이블) 항목

서버 PK는 `id`. 앱은 동기화 후 `serverId`를 보관한다.

### 4.1 User
| 필드 | 타입 | 설명 |
|---|---|---|
| id | long | |
| loginId | string | 로그인 ID |
| name | string | 표시 이름 (정길모, 담당자) |
| role | `MANAGER` \| `STAFF` | |
| isActive | bool | |
| passwordHash | (서버만) | |

### 4.2 Material (자재)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| codeNo | int | 1~500, unique |
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
| materialId | long | |
| yearMonth | string | unique (materialId + yearMonth) |
| qty | number | |

마감 시 서버가 다음 달 row를 현재고로 upsert.

### 4.4 StockMovement (입출고 이력)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | long | |
| clientUid | string | **서버 신규.** 앱 UUID. 중복 업로드 방지. unique |
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
| codeNo | int | 1~500 unique |
| name | string | FRT, CTR, STEP1 … |
| sellPrice | number | 단품판매단가 |
| isActive | bool | |
| updatedAt | long | |

### 4.6 ProductBom
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| productId | long | |
| materialId | long | unique (productId + materialId) |
| usQty | number | 대당 사용량 US. 0 초과 |
| sortOrder | int | 0~29, 최대 30행 |

US는 월평균 중량 체크값(현장 입력). 매일 바뀌지 않고 마스터로 관리.

### 4.7 DailyProduction (단품 일 실적)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| clientUid | string | **서버 신규.** UUID unique |
| productId | long | unique (productId + workDate) |
| workDate | date | |
| qty | int | 0이면 삭제와 동일 |
| updatedAt | long | **서버 권장.** 현재 앱에는 없음 |
| updatedBy | long | **서버 권장** |

같은 날·같은 단품은 **마지막 값이 정본**(덮어쓰기).

### 4.8 ProductPlan (단품 월 계획)
| 필드 | 타입 | |
|---|---|---|
| productId | long | unique with yearMonth |
| yearMonth | string | |
| qty | int | |

### 4.9 FinishedGood (완제품)
| 필드 | 타입 | 제약 |
|---|---|---|
| id | long | |
| codeNo | int | 1~26 unique |
| name | string | A1SPK 등 |
| sellPrice | number | |
| isActive | bool | |

### 4.10 FinishedComposition
| 필드 | 타입 | |
|---|---|---|
| finishedGoodId | long | |
| productId | long | unique pair, 최대 15 |
| sortOrder | int | |

수량 1 고정(현재 앱). 나중에 필요하면 `qty` 추가.

### 4.11 MonthlyPlan (완제품 월 계획)
| 필드 | 타입 | |
|---|---|---|
| finishedGoodId | long | unique with yearMonth |
| yearMonth | string | |
| qty | int | |

완제품 **일 판매실적은 없음.**

### 4.12 MonthClose
| 필드 | 타입 | |
|---|---|---|
| yearMonth | string | PK |
| closedAt | long | |
| closedBy | long | user id. 현재 앱 JSON은 문자열 |

---

## 5. API 목록

Base path 예: `https://{사내호스트}/api/v1`

### 5.1 인증

#### `POST /auth/login`
```json
{ "loginId": "jung", "password": "..." }
```
응답:
```json
{
  "accessToken": "...",
  "expiresIn": 86400,
  "refreshToken": "...",
  "user": { "id": 1, "name": "정길모", "role": "MANAGER" }
}
```

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

`codeNo` 중복 → `409 DUPLICATE_CODE`.

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
| FORBIDDEN | 403 | 역할 부족 |
| MONTH_CLOSED | 409 | 마감된 달 수정 |
| DUPLICATE_CODE | 409 | codeNo 중복 |
| DUPLICATE_UID | 200 + accepted | clientUid 이미 처리(성공으로 봐도 됨) |
| NOT_FOUND | 404 | |
| VALIDATION | 400 | 번호 범위, BOM 30 초과 등 |
| CONFLICT | 409 | 동시에 마스터를 고침. `updatedAt` 비교 |

마스터 PUT은 `If-Unmodified-Since` 또는 body `updatedAt`이 서버보다 오래되면 `CONFLICT`. 앱은 snapshot을 다시 받는다.

입출고·생산은 **추가/덮어쓰기**라 충돌이 적다. 생산은 같은 날 마지막 qty가 이긴다.

---

## 7. 앱 동기화 동작 (서버가 알면 좋은 것)

1. 가능하면 `GET /sync/snapshot` 또는 `/sync/changes`.
2. 사용자는 오프라인으로 입력 → 폰 큐에 `clientUid`와 함께 저장.
3. 통신되면 `POST /sync/push` → 성공한 uid는 큐에서 제거.
4. 실패 `MONTH_CLOSED` / `FORBIDDEN`은 사용자에게 보여주고 큐에서 빼거나 보류.
5. 앱에 “미전송 N건” 표시.

푸시(FCM)는 1차 불필요. 당김(pull)만.

---

## 8. 1차 / 2차 나눔

**1차 (동시 사용 최소)**  
`/auth/login`, `/sync/snapshot`, `/sync/push`, `/months/{ym}/close`, `/stocktake`

마스터 등록은 관리자 웹 또는 같은 API. 앱의 기존 화면과 필드가 1:1이다.

**2차**  
`/sync/changes`, `/reports`, 사용자 관리 UI, 감사 로그, 첨부 사진.

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

- HTTPS, 사내망/VPN.
- 비밀번호는 해시. 토큰 만료 짧게, refresh 사용.
- 감사: movements/production에 createdBy, 마감 closedBy.
- 백업: DB 일일 백업. 앱 JSON 백업은 비상용으로 유지.
- 개인정보: 이름·로그인 ID만. 위치·주소록 없음.
- Play 스토어에 올릴 경우 앱에 인터넷 권한·데이터 보안 문구를 다시 작성해야 함 (현재 빌드는 수집 없음).

---

## 11. 앱 쪽에서 바로 줄 수 있는 것

- 이 명세
- 앱 백업 JSON 샘플 (`설정 → 백업 내보내기`) — 테이블과 필드가 같음
- 문의: Android `kr.baraplt.material` / 패키지 필드명은 위와 동일

서버는 **JSON을 받아 DB에 넣고 snapshot을 돌려주면** 앱 연동 1차가 된다.
