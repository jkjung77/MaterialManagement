# 완성품 일일 실적 · 단품 재고

날짜: 2026-10-02  
앱: 만능자재관리 (kr.baraplt.material)

## 목적

생산(서열)과 물류(완성품)가 단품 재고로 맞물리게 한다. 완성품 날짜 실적을 넣으면 구성 단품이 빠지고, 단품 안전재고로 자재 발주를 더 일찍 잡는다. 홈에 완성품 실적 금액을 따로 보여 준다.

## 이미 있는 것 (이번 작업에서 만들지 않음)

- Room 3: `finished_production`, `product_openings`, `products.safetyStock`, `finished_composition.qty`
- 계산: `ProductSnapshot.current = opening + produced - consumed`
- `StockCalculator.productsUsedByFinished`, `requiredFromPlans`의 단품 부족분
- `AppViewModel.setFinishedProduction`, `setProductOpening`
- 서버 스냅샷·푸시: `finishedProduction`, `productOpenings`, `products.safetyStock`

## 재고 규칙

- 단품 현재고 = 이달 시작재고 + 단품 생산실적 − 완성품 투입.
- 완성품 1개 → 구성 단품마다 `qty`(기본 1) × 완성품 수량만큼 차감.
- 자재는 단품 생산실적을 넣을 때만 빠진다. 완성품 실적은 자재를 건드리지 않는다.
- 단품 현재고가 음수여도 저장은 막지 않고 그대로 표시한다.
- 단품 안전재고 > 0 이고 현재고 ≤ 안전재고이면 경보.
- 월계획 부족 자재 계산에 단품 안전재고 부족분을 더한다(이미 `requiredFromPlans`).

금액:

- 판매금액(단품) = 단품 생산 × 단품 단가 (지금 홈의 판매금액).
- 완성품 실적(금액) = 완성품 이달 실적 합 × 완성품 단가 (`MonthReport.finishedSalesAmount`).

## 화면

### 홈

- 기존 「판매금액」 칸 이름을 「판매금액(단품)」으로 바꾼다.
- 같은 줄 또는 바로 아래 줄에 「완성품 실적」 칸을 두고 금액(원)을 보여 준다. 부제에 이달 수량(대/개)을 넣는다.
- 「오늘 할 일」의 「단품 생산실적」 아래에 「완성품 실적」 버튼을 둔다. 담당자도 누를 수 있다.
- 「단품 안전재고 경보」 목록을 자재 경보와 따로 둔다. 누르면 그 단품 상세로 간다.

### 완성품 실적 (새 화면)

- 단품 생산실적과 같은 달력 UI.
- 완성품 검색(번호·이름) → 선택 → 날짜 → 수량.
- 선택 카드에 구성 단품과 이달 실적 합·금액을 보여 준다.
- 담당자 입력 가능. 마감된 달은 잠근다.
- 저장은 기존 `setFinishedProduction(finishedId, day, qty)`를 쓴다. 0이면 그날 줄을 지운다.
- 불량 칸은 두지 않는다.

경로: `Routes.FINISHED_OUTPUT = "finished_output"`. 홈 버튼과 완제품 목록에서 연다.

### 더보기 → 완제품 · 월계획

- 카드에 이달 실적 수량·금액을 추가한다.
- 안내 문구를 「월계획과 일일 실적」으로 고친다.
- 「완성품 실적 입력」 버튼을 목록 위에 둔다.

### 단품

- 수정(관리책임자): 「안전재고」, 「이달 시작재고」. 저장 시 `ProductEntity.safetyStock`과 `setProductOpening`.
- 상세: 시작재고, 이달 생산, 완성품 투입, 현재고, 안전재고.
- 복사해서 새로 만들기: 안전재고는 복사, 시작재고·실적은 비움.

### 실적 탭

- 완성품 이달 실적 합·금액을 한 줄 추가한다. 단품 목록에는 현재고를 보여 준다.

## 동기화

- 완성품 실적 저장 → 기존처럼 `finished_production`을 대기열에 넣고 `syncAfterChange`.
- 단품 안전재고·시작재고는 기초정보이므로 `markMastersDirty` 후 동기화(시작재고는 이미 그렇게 함).
- 서버 API·테이블은 추가하지 않는다.

## 테스트

순수 Kotlin만 (org.json 금지).

- `StockCalculator.productsUsedByFinished`: 구성 qty, 0 실적, 같은 단품이 여러 완성품에 있을 때.
- `ProductSnapshot.current` / `stockLow`.
- 기존 `FinishedPlanRollup` 테스트는 유지.

## 하지 않음

- 완성품 실적으로 단품 생산·자재를 자동 입력하지 않음.
- Room 버전을 4로 올리지 않음. `fallbackToDestructiveMigration`으로 공장 데이터를 지우지 않음.
- 완성품 안전재고, 완성품 현재고, 완성품 시작재고는 이번 범위 밖.
- 앱 표시 이름 「완제품」을 「완성품」으로 전면 교체하지 않음. 새 버튼·홈 칸만 「완성품 실적」.
- 버전 올리기·번들 빌드는 사용자가 요청할 때만.

## 완료 기준

- 홈에서 단품 판매금액과 완성품 실적 금액이 따로 보인다.
- 담당자가 완성품을 날짜별로 넣으면 구성 단품 현재고가 줄어든다.
- 단품 안전재고 이하이면 홈에 단품 경보가 뜬다.
- 단위 테스트와 `compileReleaseKotlin`이 통과한다.
