# 구현 기록 — 2주차

`CLAUDE.md` 의 TDD 규칙(`RED → GREEN → REFACTOR`)에 따라 규칙 하나씩 구현한다.
각 항목의 RED 는 실행해서 확인한 실패다.

## 구현 순서

의존이 없는 것부터 쌓는다. 주문이 마지막인 이유는 앞의 넷이 있어야 확정 흐름을 검증할 수 있어서다.

```
1  브랜드        의존 없음                      ← 도메인·고객 조회 완료
2  상품          브랜드 참조, 재고 보유          ← 도메인 완료
3  좋아요        상품 참조, 관계 모델            ← 도메인 완료
4  포인트        의존 없음                      ← 도메인 완료
5  주문          상품·재고·포인트를 모두 사용    ← 도메인 완료
```

---

## 1. 브랜드

### 구현 전 정한 것

| 항목 | 결정 |
| --- | --- |
| 관리자 식별 | `X-USER-ID` + 역할 헤더. `ADMIN` 이 아니면 거절한다 |
| 브랜드 이름 | 공백 불가, 최대 50자, 이름 중복 거절 |
| 삭제 | soft delete. `BaseEntity` 의 `deletedAt` 을 사용한다 |

### 만든 파일

| 계층 | 파일 |
| --- | --- |
| domain | `domain/brand/Brand.kt`, `BrandRepository.kt`, `BrandService.kt` |
| infrastructure | `infrastructure/brand/BrandJpaRepository.kt`, `BrandRepositoryImpl.kt` |
| application | `application/brand/BrandFacade.kt`, `BrandInfo.kt` |
| interfaces | `interfaces/api/brand/BrandV1Controller.kt`, `BrandV1Dto.kt` |
| test | `domain/brand/BrandTest.kt`, `BrandServiceIntegrationTest.kt`, `interfaces/api/BrandV1ApiE2ETest.kt` |

### TDD 기록

| # | 규칙 | RED — 무엇이 어떻게 실패했나 | GREEN — 무엇을 더했나 |
| --- | --- | --- | --- |
| 1 | 이름이 공백이면 거절한다 | `BrandTest.kt:41` — 검사가 없어 예외가 던져지지 않음 | `Brand.init` 에 `isBlank` 검사 |
| 2 | 이름이 50자를 넘으면 거절한다 | 같은 이유로 실패 | `MAX_NAME_LENGTH` 상수와 길이 검사 |
| 3 | 유효한 이름으로 생성된다 | 없음 — 이미 통과 | 없음. 동작을 고정하는 테스트다 |
| 4 | 같은 이름을 두 번 등록하면 거절한다 | 통합 테스트 3건 동시 실패 | `BrandService.create` 에 `CONFLICT` |
| 5 | 없는 브랜드 조회는 `NOT_FOUND` | 위와 같음 | `BrandService.get` 에 `NOT_FOUND` |
| 6 | 삭제된 브랜드 조회는 `NOT_FOUND` | 위와 같음 | 저장소에서 `findByIdAndDeletedAtIsNull` 로 거름 |

3번은 RED 가 없다. 거절 사례만 검증하면 모든 요청을 거절하는 구현도 통과하므로 정상 사례를 함께 둔다.

### 검증

`./gradlew :apps:commerce-api:test` — 전체 통과 (ArchUnit 포함)
`./gradlew :apps:commerce-api:ktlintCheck` — 통과

### 남은 것

- **관리자 CRUD** — `GET/POST /api-admin/v1/brands`, `GET/PUT/DELETE /api-admin/v1/brands/{brandId}`.
  역할이 아닌 요청을 어떤 응답으로 거절할지 정해야 한다. 현재 `ErrorType` 에 401·403 이 없고,
  W1 에서 주문 소유권은 404 로 숨기기로 한 것과 맞물린다.
- **브랜드 삭제의 연결 상품 확인** — 상품을 구현한 뒤에 붙인다. 지금 만들면 검증할 대상이 없다.

---

## 2. 상품

### 구현 전 정한 것

| 항목 | 결정 |
| --- | --- |
| 상품 이름 | 공백 불가, 최대 100자 |
| 상품 가격 | 1원 이상. `Money` 자체는 0원을 허용하지만 상품 가격은 양수여야 한다 |
| 값 객체 | `Stock` · `Money` 둘 다 VO 로 뽑는다. 주문·포인트에서 다시 쓴다 |
| 브랜드 참조 | 객체가 아니라 `brandId` 만 보관한다. 조회 결과 조합은 application 이 맡는다 |
| 삭제 | soft delete. 재고가 0일 때만 삭제한다 |

### 만든 파일

| 계층 | 파일 |
| --- | --- |
| domain | `domain/shared/Money.kt`, `domain/product/Stock.kt`, `Product.kt`, `ProductRepository.kt`, `ProductService.kt` |
| infrastructure | `infrastructure/product/ProductJpaRepository.kt`, `ProductRepositoryImpl.kt` |
| test | `domain/shared/MoneyTest.kt`, `domain/product/StockTest.kt`, `ProductTest.kt`, `ProductServiceIntegrationTest.kt` |

### TDD 기록

값 객체는 규칙이 한 덩어리로 묶여 있어 객체 단위로 RED 를 만들었다.

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | `Money` — 음수 거절 · 차감액 초과 거절 · 합계 범위 초과 거절 | 6건 중 3건 실패 | `init` 검사, `minus` 관계 검사, `plus` 에 `Math.addExact` |
| 2 | `Stock` — 음수 거절 · 차감 수량 0 이하 거절 · 보유량 초과 거절 · 복원 수량 0 이하 거절 | 8건 중 4건 실패 | `init` 검사와 `requirePositive`, `decrease` 의 보유량 비교 |
| 3 | `Product` — 이름 공백·100자 초과 거절, 가격 0원 거절, 수정 시 기존 값 유지 | 8건 중 4건 실패 | `guardName` · `guardPrice` 를 `init` 과 `update` 양쪽에서 호출 |
| 4 | 없거나 삭제된 브랜드를 참조하면 `NOT_FOUND` | 통합 7건 중 5건 실패 | `ProductService.requireExistingBrand` |
| 5 | 없거나 삭제된 상품 조회는 `NOT_FOUND` | 위와 같음 | `ProductService.get` 과 `findByIdAndDeletedAtIsNull` |
| 6 | 재고가 남아 있으면 삭제를 거절한다 | 위와 같음 | `ProductService.delete` 에 `CONFLICT` |

값의 유효성과 행동의 입력 조건을 나눠 두었다. `Money` 는 0원을 허용하고, 0원을 거절하는 것은
상품 가격이라는 **행동의 조건**이므로 `Product.guardPrice` 에 있다.

수정 실패 시 기존 값이 유지되는지도 함께 검증한다. 검사를 통과한 뒤에 대입하기 때문이다.

### 검증

`./gradlew :apps:commerce-api:test` — 전체 통과 (ArchUnit 포함)
`./gradlew :apps:commerce-api:ktlintCheck` — 통과

### 남은 것

- **상품 상세·목록 API** — 상세 응답에 좋아요 수가 들어가므로 좋아요를 먼저 만든다.
- **목록 정렬** — `latest` · `price_asc` · `likes_desc` 의 동률 보조 기준과 잘못된 입력 처리를 정해야 한다.
- **관리자 CRUD** — 브랜드와 같은 이유로 역할 거절 응답을 정해야 한다.

---

## 3. 좋아요

유스케이스 4번(등록) · 5번(해제)에 해당한다.

### 구현 전 정한 것

| 항목 | 결정 |
| --- | --- |
| 저장 방식 | 사용자–상품 관계로 저장한다. 별도 카운터를 두지 않고 관계에서 센다 |
| 중복 방지 | `(user_id, product_id)` 유니크 제약 |
| 등록 | 삭제된 상품이면 거절한다 |
| 해제 | 상품의 존재·삭제 여부를 보지 않는다 |

해제 조건은 흐름도와 과제 명세가 갈렸다. 흐름도 5번은 삭제된 상품이면 거절이었고,
명세는 "남아 있는 자신의 좋아요 관계는 취소할 수 있게" 였다. 명세를 따르고 흐름도를 고쳤다.
삭제된 상품에 좋아요가 묶인 채 지울 수 없는 상태가 남지 않도록 하기 위해서다.

### 만든 파일

| 계층 | 파일 |
| --- | --- |
| domain | `domain/like/Like.kt`, `LikeRepository.kt`, `LikeService.kt` |
| infrastructure | `infrastructure/like/LikeJpaRepository.kt`, `LikeRepositoryImpl.kt` |
| test | `domain/like/LikeServiceIntegrationTest.kt` |
| 문서 | `usecase.md` 5번 흐름도 수정 |

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 없거나 삭제된 상품에는 좋아요를 등록할 수 없다 | 5건 중 4건 실패 | `LikeService.like` 에서 `productService.get` 호출 |
| 2 | 이미 눌렀어도 성공하고 관계는 하나만 남는다 | 위와 같음 | `find` 후 없을 때만 `save` |
| 3 | 좋아요 수는 관계에서 센다 | 위와 같음 | `countByProductId` |
| 4 | 누른 적이 없어도 해제는 성공한다 | **확인하지 못했다** | `find` 후 있을 때만 `delete` |
| 5 | 삭제된 상품에 남은 자신의 관계는 취소할 수 있다 | **확인하지 못했다** | 해제에서 상품 조회를 하지 않는다 |
| 6 | 다른 사용자의 관계는 지우지 않는다 | **확인하지 못했다** | `find(userId, productId)` 로 자신의 관계만 찾는다 |

4~6번은 테스트와 구현을 한 번에 넣어 RED 를 따로 실행하지 않았다. 규칙 위반이며,
"실행되지 않은 테스트를 실패 확인으로 기록하지 않는다" 에 따라 여기에 그대로 남긴다.

### 등록과 해제의 비대칭

```
like   →  productService.get(productId)   // 삭제된 상품이면 404
unlike →  (상품 상태를 보지 않음)           // 남은 관계는 지울 수 있다
```

의도한 차이이므로 `LikeService` 에 주석으로 이유를 남겼다.

### 검증

`./gradlew :apps:commerce-api:test` — 전체 통과 (ArchUnit 포함)
`./gradlew :apps:commerce-api:ktlintCheck` — 통과

### 남은 것

- **좋아요 API** — `POST/DELETE /api/v1/products/{productId}/likes`, `GET /api/v1/users/{userId}/likes`.
  내 좋아요 목록에서 삭제된 상품을 제외해야 한다.
- **상품 삭제의 좋아요 삭제** — 유스케이스 7번의 `좋아요 삭제` 단계가 아직 비어 있다. 이제 붙일 수 있다.

---

## 4. 포인트

유스케이스 8번(포인트 충전)에 해당한다.

### 구현 전 정한 것

| 항목 | 결정 |
| --- | --- |
| 충전액 | 0 이하를 거절한다. 0원은 충전이 아니다 |
| 잔액 | 0원도 유효하다. 충전한 적이 없는 사용자의 잔액은 0원이다 |
| 합계 범위 | `Money.plus` 의 `Math.addExact` 가 막는다 |
| 사용자 | 첫 충전 때 잔액이 만들어진다. 별도 사용자 조회 단계를 두지 않는다 |

흐름도 8번에 `사용자 조회` 단계와 그 실패 경로가 없으므로 존재 확인을 넣지 않았다.

### 만든 파일

| 계층 | 파일 |
| --- | --- |
| domain | `domain/point/PointBalance.kt`, `PointRepository.kt`, `PointService.kt` |
| infrastructure | `infrastructure/point/PointJpaRepository.kt`, `PointRepositoryImpl.kt` |
| test | `domain/point/PointBalanceTest.kt`, `PointServiceIntegrationTest.kt` |

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 충전액이 0원이면 거절하고 기존 잔액을 유지한다 | 단위 5건 중 1건 실패 | `PointBalance.charge` 에 양수 검사 |
| 2 | 충전 후 잔액이 표현 범위를 넘으면 거절한다 | 없음 — 이미 통과 | 없음. `Money.plus` 가 이미 막는다 |
| 3 | 두 번 충전하면 기존 잔액에 더해진다 | 통합 5건 중 2건 실패 | `findByUserId` 로 기존 잔액을 찾아 충전 |
| 4 | 충전한 적이 없으면 잔액은 0원이다 | 위와 같음 | `getBalance` 에서 없으면 `Money.ZERO` |

2번은 RED 가 없다. 값의 유효성(`Money`)과 행동의 입력 조건(`charge`)을 나눠 둔 덕분에
행동 쪽에서 다시 검사할 필요가 없었다. 상품 가격에서 쓴 구분과 같다.

### 검증

`./gradlew :apps:commerce-api:test` — 전체 통과 (ArchUnit 포함)
`./gradlew :apps:commerce-api:ktlintCheck` — 통과

### 남은 것

- **포인트 API** — `POST /api/v1/points/charge`, `GET /api/v1/points`.
  누락·잘못된 타입은 요청 DTO 검증으로 거른다.

---

## 5. 주문

유스케이스 1번(주문)에 해당한다.

### 흐름도와 API 의 대응

흐름도의 한 줄이 API 두 개로 갈린다.

```
POST /api/v1/orders          주문서 제작 → DRAFT 저장            차감 없음
POST /orders/{id}/confirm    재고 차감 → 포인트 차감 → 주문 확정   CONFIRMED
```

`decisions.md` 의 "재고는 주문 확정 전에 차감" 은 확정 API **안에서의 순서**로 유지된다.

### 구현 전 정한 것

| 항목 | 결정 |
| --- | --- |
| 쿠폰 | 이번 범위에서 제외한다. 과제 명세의 이번 주 표에 쿠폰 API 가 없다 |
| 중복 품목 | 같은 상품이 두 번 들어오면 거절한다 |
| 단가 | 주문 생성 시점의 상품 가격으로 고정한다 |
| 결제액 | 주문 합계와 같아야 한다. 다르면 확정을 거절한다 |
| 남의 주문 | 없는 주문과 같은 `NOT_FOUND`. 주문의 존재가 새어나가지 않는다 (W1 INV-001) |

### 만든 파일

| 계층 | 파일 |
| --- | --- |
| domain | `domain/order/Order.kt`, `OrderItem.kt`, `OrderStatus.kt`, `OrderCommand.kt`, `OrderRepository.kt`, `OrderService.kt` |
| infrastructure | `infrastructure/order/OrderJpaRepository.kt`, `OrderRepositoryImpl.kt` |
| 추가된 행동 | `Money.times`, `PointBalance.pay`, `PointService.pay` |
| test | `domain/order/OrderItemTest.kt`, `OrderTest.kt`, `OrderServiceIntegrationTest.kt` |

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | `Money.times` — 수량 0 이하 거절, 범위 초과 거절 | 9건 중 1건 실패 | `requirePositive` 와 `Math.multiplyExact` |
| 2 | 주문 품목의 수량은 양수여야 한다 | 2건 중 1건 실패 | `OrderItem.init` |
| 3 | 품목이 없거나 같은 상품이 중복되면 거절한다 | 8건 중 4건 실패 | `Order.init` 의 `isEmpty` · `distinctBy` |
| 4 | 결제액이 합계와 다르면 확정을 거절한다 | 위와 같음 | `Order.confirm` 의 금액 비교 |
| 5 | 이미 확정된 주문은 다시 확정할 수 없다 | 위와 같음 | `Order.confirm` 의 상태 검사 |
| 6 | `PointBalance.pay` — 결제액 0 이하 거절 | 9건 중 1건 실패 | `requirePositive` 추출 (REFACTOR) |
| 7 | 생성 시 재고가 줄지 않고 단가가 고정된다 | 통합 8건 전부 실패 | `OrderService.create` 에서 상품 가격을 읽어 `OrderItem` 생성 |
| 8 | 남의 주문은 없는 주문과 같은 응답 | 위와 같음 | `get` 에서 `isOwnedBy` 로 거른 뒤 `NOT_FOUND` |
| 9 | 재고 부족이면 포인트가 줄지 않는다 | 위와 같음 | 재고를 결제보다 먼저 차감 |
| 10 | 잔액 부족이면 재고가 복원된다 | 위와 같음 | 같은 트랜잭션 안이므로 롤백이 되돌린다 |

6번은 충전과 결제가 같은 검사를 쓰게 되어 `requirePositive` 로 묶었다. 동작을 바꾸지 않고
중복만 없앤 REFACTOR 다.

10번은 보상 코드를 쓰지 않았다. 재고·포인트·주문이 같은 DB 라 트랜잭션이 되돌린다.
흐름도의 `재고 복원` 박스는 PG 가 들어와 한 트랜잭션이 깨질 때 실제 코드가 된다.

### 차감 순서

```
재고 차감  →  포인트 차감  →  주문 확정
```

되돌리기 싼 것부터 둔다. 지금은 셋 다 같은 DB 라 순서를 바꿔도 결과가 같지만,
외부 결제가 들어오면 이 순서만 보상 없이 버틴다.

### 검증

`./gradlew :apps:commerce-api:test` — 전체 통과 (ArchUnit 포함)
`./gradlew :apps:commerce-api:ktlintCheck` — 통과

### 남은 것

- **주문 API** — `POST /api/v1/orders`, `POST /orders/{orderId}/confirm`, `GET /api/v1/orders`, `GET /orders/{orderId}`.
- **주문 취소** — 유스케이스 2·3번. 아직 `CANCELED` 상태와 복원 흐름이 없다.
- **관리자 주문 조회** — 역할 거절 응답을 정해야 한다.

---

## 6. API 계층

도메인 다섯 개가 HTTP 로 이어졌다.

| 메서드 · 경로 | 유스케이스 |
| --- | --- |
| `GET /api/v1/brands/{brandId}` | — |
| `GET /api/v1/products/{productId}` | — (브랜드명·좋아요 수 조합) |
| `POST · DELETE /api/v1/products/{productId}/likes` | 4 · 5 |
| `POST /api/v1/points/charge`, `GET /api/v1/points` | 8 |
| `POST /api/v1/orders`, `POST /{orderId}/confirm` | 1 |
| `GET /api/v1/orders`, `GET /{orderId}` | — |

### 정한 것

| 항목 | 결정 |
| --- | --- |
| 요청자 식별 | `X-USER-ID` 헤더. `User` 엔티티를 두지 않고 헤더 값을 그대로 쓴다 |
| 헤더 누락 | `400 BAD_REQUEST` |

### TDD 기록

| # | 대상 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 고객 API 4종 | E2E 31건 중 25건 실패 | Facade 4개와 컨트롤러 4개 |
| 2 | 헤더 누락이 500 으로 떨어짐 | 1건 실패 | `ApiControllerAdvice` 에 `MissingRequestHeaderException` → 400 |

`ApiControllerAdvice` 는 공용 파일이며 핸들러 한 블록만 더했다.

---

## 7. 주문 취소 — 유스케이스 2 · 3

### 재고 차감 시점을 옮겼다

유스케이스 2번에 `재고 복원` 이 있으려면 확정 전에 이미 차감돼 있어야 한다. 그래서 차감을
`confirm` 에서 `create` 로 옮겼다. 흐름도가 기준이다.

```
주문 생성   재고 차감 + 주문서 저장 (DRAFT)
주문 확정   포인트 차감 + 금액 고정 (CONFIRMED)
주문 취소   재고 복원 (항상) + 확정된 주문이면 포인트 복원
```

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 이미 취소된 주문은 다시 취소할 수 없다 | 단위 12건 중 1건 실패 | `Order.cancel` 의 상태 검사 |
| 2 | 취소된 주문은 확정할 수 없다 | 없음 — 이미 통과 | 없음. `confirm` 이 `DRAFT` 만 허용한다 |
| 3 | 생성 시 재고가 차감된다 | 통합·E2E 기대값 변경으로 실패 | `OrderService.create` 에서 `decreaseStock` |
| 4 | 확정 전 취소는 재고를 복원한다 | 통합 1건 실패 | `cancel` 에서 항상 `increaseStock` |
| 5 | 확정된 주문 취소는 포인트도 복원한다 | 위와 같음 | `isConfirmed()` 일 때 `PointService.refund` |

포인트 복원은 `refund` 라는 이름을 따로 두었다. 잔액이 늘어난다는 점은 충전과 같지만
호출부에서 사유가 드러나야 하기 때문이다.

---

## 8. 브랜드 · 상품 삭제 — 유스케이스 6 · 7

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 삭제되지 않은 상품이 남아 있으면 브랜드를 삭제할 수 없다 | 통합 5건 중 2건 실패 | `BrandService.delete` 에 `countActiveByBrandId` 검사 |
| 2 | 재고가 0이 아니어도 상품이 남아 있으면 거절한다 | 위와 같음 | 세는 것은 재고가 아니라 삭제되지 않은 상품 수다 |
| 3 | 상품을 삭제하면 좋아요 관계도 지워진다 | 통합 1건 실패 | `ProductService.delete` 에서 `deleteAllByProductId` |
| 4 | 관리자 삭제 API | E2E 7건 실패 | `AdminRole.guard` 와 관리자 컨트롤러 2개 |

2번이 핵심이다. 브랜드 삭제 조건은 **재고가 아니라 상품의 존재**를 본다.
상품은 soft delete 지만 좋아요 관계는 실제로 지운다. 관계는 되살릴 대상이 아니다.

---

## 9. 브랜드 CRUD — 유스케이스 9 · 10 · 11 · 12

브랜드는 이것으로 CRUD 가 끝났다.

```
GET    /api-admin/v1/brands            목록. 삭제된 브랜드 제외, 없으면 빈 List
POST   /api-admin/v1/brands            생성
GET    /api-admin/v1/brands/{brandId}  상세
PUT    /api-admin/v1/brands/{brandId}  수정
DELETE /api-admin/v1/brands/{brandId}  삭제
GET    /api/v1/brands/{brandId}        고객 상세
```

### 이름 규칙이 바뀌었다

유스케이스 9번의 그림대로 **4자리 이상 8자리 이하**로 바꿨다. 이전에는 최대 50자였다.
메시지도 그림 문구를 그대로 쓴다.

```
브랜드 명은 공백일 수 없습니다.
브랜드명은 4자리 이상 8자리 이하입니다.
```

이름 중복 거절은 **뺐다.** 유스케이스 9번에 없기 때문이다.

이 변경으로 `"나이키"`(3자)를 쓰던 테스트가 규칙에 걸려 `"나이키코리아"` 로 바꿨다.
`"브랜드${System.nanoTime()}"` 같은 무작위 이름도 8자를 넘어 `"테스트브랜드"` 로 바꿨다.
그 무작위 이름은 원래 중복 거절을 피하려던 것이라, 중복 규칙이 사라진 뒤로는 필요가 없었다.

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 이름은 4자 이상 8자 이하다 | 단위 6건 중 2건 실패 | `guardName` 의 범위 검사 |
| 2 | 수정 이름도 같은 규칙을 지키고, 실패하면 기존 이름이 유지된다 | 단위 10건 중 3건 실패 | `update` 에서 `guardName` 호출 후 대입 |
| 3 | 브랜드 생성 API | E2E 6건 실패 | `BrandFacade.createBrand` 와 `POST` |
| 4 | 상세·목록·수정 API | E2E 9건 실패 | `getAll` · `update` 와 컨트롤러 3개 |

2번은 검사 후 대입 순서가 핵심이다. 먼저 대입하면 거절해도 값이 바뀐 뒤다.

목록은 브랜드가 없을 때 빈 List 를 반환한다. 유스케이스 11번에 거절 경로가 없다.

### 검증

`./gradlew :apps:commerce-api:test` — 168건 전체 통과 (ArchUnit 포함)
`./gradlew :apps:commerce-api:ktlintCheck` — 통과

### 아직 없는 것

- **상품 CRUD** — 관리자 등록·수정·목록·상세와 재고 변경. 삭제만 있다.
- **고객 상품 목록** — 브랜드 필터·페이징·정렬. 정렬 동률 보조 기준이 미정이다.
- **내 좋아요 목록** — `LikeService` 에 조회 메서드가 없다.
- **관리자 주문 목록·상세** — 도메인은 있고 컨트롤러만 없다.
- **쿠폰 · PG** — 유스케이스 1번의 선택 단계이며 범위 밖이다.
