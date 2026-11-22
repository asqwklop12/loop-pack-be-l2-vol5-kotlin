# 결제 응답 유실 사고 실험 기획

2026-11-22 기획. 1번(사고 범위와 정상 기준선)부터 하나씩 진행한다.

## 진행 기록

| 날짜 | 단계 | 한 일 | 결과 |
| ---- | ---- | ----- | ---- |
| 2026-11-22 | 1 | `LocalPgFixture`·`LocalPgFixtureTest`·`CheckoutIncidentTest`(baseline) 추가. 프로덕션 코드 변경 없음 | fixture 5개, baseline 1개 통과. ktlint·ArchUnit 통과 |

1번에서 알게 된 것:

- 2번 로그 작업 때 주의: PG timeout 은 예외 이름이 아니라 원인이 `SocketTimeoutException` 인지로 판단한다. 실제로는 `ResourceAccessException` 이 아니라 `RestClientException` 으로 와서, 이름으로 가르면 timeout 을 다른 실패로 잘못 기록한다.
- baseline 실측: Payment `SUCCESS` 10, PG 거래 10(모두 `SUCCESS`, 금액 39,000), create 10(중복 0), 재고 예약 `COMMITTED` 10, 상품 재고 30 → 20, 포인트 50,000 그대로, management 포트의 health `UP`.
- 테스트 설정: `pg.read-timeout=500ms`, fixture 의 응답 유실 대기 1.5s, `management.server.port=0`(랜덤).

## 목표

health 는 UP 인데 고객은 "결제 확인 중"을 보는 사고를 로컬 테스트에서 일부러 만든다. 증거로 원인을 가리고, 새 PG 결제 없이 같은 주문을 마무리한다. 그 뒤 재고·Outbox·구매 활동·주간 랭킹까지 같은 주문 집합으로 맞춘다.

통제하는 경계는 하나다. **PG 는 거래를 SUCCESS 로 저장했는데 그 생성 응답을 잃어서 내부 결제가 미확인으로 남는 경계**.

범위 밖: 실제 카드·계정, 외부 PG 장애, 운영 DB 변경, 부하, 분산 추적 backend 구축.

## 지금 코드의 상태 (main `7380737` 기준)

| 구간 | 상태 | 근거 |
| ---- | ---- | ---- |
| 주문·재고 확보 | 있음 | `OrderConfirmation.start` 한 TX 에서 재고 예약, Payment `PENDING` 저장, 주문 `CONFIRMED` |
| PG 결제 요청 | 있음 | `PgClient.request`, read-timeout 3s, 서킷 브레이커 `pg` |
| 결과 반영: 콜백 | 있음 | `PaymentCallbackHandler` → `PaymentSettlement.reflect` |
| 결과 반영: 조회(reconcile) | 없음 | `PaymentGateway.findByOrderId` 는 `PgClient` 에 구현만 있고 부르는 곳이 없다 |
| 주문 확정 → Outbox | 연결 안 됨 | `OrderEventOutboxWriter`·`commerce-relay` 는 있지만, 프로덕션 코드에서 `OrderConfirmedEvent` 를 만들거나 `append` 를 부르는 곳이 없다. 테스트가 이벤트를 직접 넣는다 |
| Kafka → 구매 활동 | 있음 | `commerce-streamer` `PurchaseActivityConsumer` |
| 주간 랭킹 | 있음 | W9 스냅샷·랭킹 배치, `GET /api/v1/rankings/weekly` |
| 쿠폰 | 있음 | W8. 결제와 무관하고 보존만 한다 |

관측:

- health 는 liveness·readiness 만 본다 (`supports/monitoring/.../monitoring.yml`). 결제가 꼬여도 UP 이다.
- 지표는 `http.server.requests` 뿐이고 업무 지표가 없다.
- 결제 요청과 timeout 시점에 orderId 가 찍히는 로그가 없다. timeout 은 `ApiControllerAdvice` 의 `Exception : Read timed out` 한 줄로만 남는다. 콜백 로그에만 orderId·transactionKey 가 있다.

## 과제 용어와 이 코드의 대응

주문은 PG 호출 전에 확정된다. 그래서 미확인은 주문이 아니라 Payment 에 남는다.

| 과제 표현 | 이 코드 |
| --------- | ------- |
| 내부 성공 | Payment `SUCCESS` |
| UNKNOWN | Payment `PENDING` (UNKNOWN 상태를 새로 만들지 않는다) |
| paymentAttemptKey | `Payment.paymentKey` |
| providerOrderId | PG 요청의 `orderId` |
| 거래 키 | PG `transactionKey` |

## 정한 것

- **브랜치**: main 에서 딴 `feat/volume10-incident`.
- **PG fixture**: commerce-api 테스트 안에 HTTP 서버를 둔다. pg-simulator 와 같은 계약(`POST`·`GET /api/v1/payments`, commerce-api 로 콜백)을 따른다. pg-simulator 를 컨테이너나 테스트 의존으로 띄우지 않는다. 앱끼리 의존이 생기고, 응답 유실·콜백 보류·create 수를 테스트가 직접 제어하기 어렵기 때문이다.
- **DB**: Testcontainers 로 매 실행 새 DB 에서 시작한다. baseline → fault → recovery 사이에는 비우지 않는다.
- **데이터**: 브랜드·상품·재고·사용자·포인트는 테스트 시작 때 저장한다. 주문과 결제 상태는 실제 API 흐름으로만 만든다. Payment 상태를 DB 에 직접 쓰지 않는다.

## 단계별 계획

### 1. 사고 범위와 정상 기준선

바꿀 파일은 전부 `apps/commerce-api/src/test` 아래다.

- `LocalPgFixture`
  - 랜덤 포트 HTTP 서버.
  - `POST /api/v1/payments`: 거래 키를 만들어 `PENDING` 거래를 저장하고 create 수를 센다. 같은 주문의 두 번째 create 는 기록해 두고 테스트를 실패시킨다.
  - `GET /api/v1/payments?orderId=`: 현재 거래.
  - `settle(orderId)`: 거래를 `SUCCESS` 로 바꾸고 `/api/v1/payments/callback` 으로 보낸다.
  - 응답 유실 제어(2단계부터 사용): 지정한 주문은 거래를 저장한 뒤 commerce-api read-timeout 보다 오래 붙잡는다. 지정한 주문의 콜백은 보류한다.
- `LocalPgFixtureTest`: 중복 create 가 기록되는지, 응답 유실 모드에서도 거래가 남는지.
- `CheckoutIncidentTest` (baseline 까지)
  - 앱 랜덤 포트, management 랜덤 포트, `pg.base-url` 은 fixture 주소.
  - 상품 1개, 재고 30, 주문마다 다른 사용자, 카드 결제 10건.
  - fixture `settle` → 콜백 → Payment `SUCCESS`.

baseline 기대값:

| 항목 | 기대 |
| ---- | ---- |
| Payment `SUCCESS` / `PENDING` | 10 / 0 |
| fixture `SUCCESS` 거래 | 10 |
| create 수 | 10 |
| 재고 예약 확정 | 10 |
| health | UP |

### 2. 관측 연결

- 결제 요청·응답·timeout 경계에 orderId·paymentKey·transactionKey 를 남기는 구조화 로그.
- 미종결 결제 수 gauge 하나. 이름과 범위(PENDING 전체를 세는지)는 이 단계에서 정한다. label 에 식별자를 넣지 않는다.
- 테스트가 실제 Actuator health 와 Prometheus 응답을 가져와 gauge 가 존재하고 바뀌는지 확인한다.

### 3. 같은 상태를 유지하는 사고 실험

한 테스트 메서드에서 baseline → fault → 증거 보존 → recovery 를 잇는다.

fault 기대값 (별도 장애 집합 10건, 그중 고정한 3건만 응답 유실·콜백 보류):

| 항목 | 기대 |
| ---- | ---- |
| Payment `SUCCESS` / `PENDING` | 7 / 3 |
| fixture `SUCCESS` 거래 | 10 (누적 20) |
| health | UP |

실패해도 `finally` 에서 켠 주입은 끄되, 거래와 증거는 지우지 않는다.

### 4. 경쟁 가설

조치 전에 가설과 예상 관찰을 적고, 진단용 조회는 읽기만 한다. 복구 API 는 fault 증거를 보존한 뒤에만 부른다.

### 5. 같은 주문 복구

본인 주문 reconcile API 로 같은 3건을 조회해 기존 `PaymentSettlement` 로 반영한다. create 추가 0, 재고 추가 차감 없음, 포인트·쿠폰 불변, 반복 호출해도 같은 결과.

### 6. 후속 결과

같은 장애 집합의 Outbox → relay → Kafka → 구매 활동 → 새 스냅샷 → 랭킹 API. baseline 10 + 장애 10 이 같은 주에 들어가면 상품 점수 20.

### 7. 증거와 runbook

실행마다 새 증거 위치에 세 단계 기록을 남기고 `docs/runbook/week10.md` 를 쓴다.

### 8. 회귀와 남은 위험

관련 회귀·ktlint·check 결과와, 이번에 확인하지 않은 위험을 구분해 적는다.

## 아직 정하지 않은 것

- **reconcile 경로를 이번에 만들지**: 과제는 W5 에 "본인 주문 reconcile API"가 있다고 전제하는데 없다. 5단계 전에 정한다.
- **주문 확정 → Outbox 연결**: 끊겨 있어서 6단계가 성립하지 않는다. 연결한다면 이벤트를 주문 확정 시점(PG 호출 전)에 낼지, 결제 성공 시점에 낼지 정해야 한다. 지금 구조대로 주문 확정 때 내면 미확인 결제의 구매 활동도 랭킹에 들어간다.
- **사용자 범위**: PG 계약(`PgDto.PaymentRequest`)에 사용자 필드가 없다. "같은 계정" 검증을 무엇으로 할지.
- **미종결 gauge 이름과 범위**: 2단계에서 정한다.
