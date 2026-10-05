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

> 이 규칙은 9절에서 바뀐다. 유스케이스 9번을 그리면서 **4~8자**가 되고 중복 거절은 빠졌다.
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

이어지는 10~13 절에서 상품 CRUD, 관리자 주문 조회, 고객 목록 두 개를 채웠다.

---

## 10. 상품 CRUD — 유스케이스 13 · 14 · 15 · 16 · 17

```
GET · POST          /api-admin/v1/products
GET · PUT · DELETE  /api-admin/v1/products/{productId}
PUT                 /api-admin/v1/products/{productId}/stock
```

### 바뀐 규칙 둘

**이름이 2자 이상 20자 이하다.** 이전에는 1~100자였다. 메시지도 그림 문구를 쓴다.

**생성이 재고를 받지 않는다.** 항상 `Stock(0)` 으로 시작하고 재고 변경으로만 바꾼다.
유스케이스 13번이 `상품 생성` 과 `재고 생성 (초기값 0)` 을 다른 박스로 그렸기 때문이다.

### 재고 변경은 설정이 아니라 증가다

유스케이스 17번이 `재고 증가 값 입력` 이라 최종 수량을 설정하지 않고 증가 값을 더한다.
과제 명세의 "0 이상인 최종 수량 설정" 과 다르며 그림을 따랐다.

음수만 거절하므로 **0 은 통과하고 재고가 바뀌지 않는다.** `Stock.increase` 는 여전히 양수만
받는다. 0 허용은 유스케이스 층의 판단이라 `ProductService.changeStock` 에서 걸렀다.
주문 취소의 재고 복원은 엄격한 채로 남는다.

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 이름은 2자 이상 20자 이하다 | 단위 11건 중 2건 실패 | `guardName` 의 범위 검사 |
| 2 | 생성 시 재고는 0이다 | E2E 14건 중 13건 실패 | `create` 에서 `stock` 파라미터 제거, `INITIAL_STOCK` 상수 |
| 3 | 재고 변경은 증가 값을 더한다 | E2E 19건 중 4건 실패 | `changeStock` 에서 `increaseStock` 호출 |
| 4 | 음수 증가 값은 거절하고 기존 재고를 유지한다 | 위와 같음 | `amount < 0` 검사 |

---

## 11. 주문 조회 — 유스케이스 18 · 19 · 20

```
GET /api-admin/v1/orders             모든 구매자의 주문
GET /api-admin/v1/orders/{orderId}   소유자를 가리지 않음
GET /api/v1/orders/{orderId}         내 주문만 (이미 있었음)
```

### 고객과 관리자의 조회를 메서드로 갈랐다

```kotlin
get(userId, orderId)   // 고객 — 소유자가 아니면 없는 것과 같은 404
getAnyOrder(orderId)   // 관리자 — 소유자를 보지 않음
```

이름을 나눈 이유는 관리자 경로가 **실수로 소유자 검사를 건너뛴 것이 아니라 의도한 것**임을
드러내기 위해서다. 같은 메서드에 플래그를 넘기면 그 구분이 사라진다.

유스케이스 20번은 `내 주문이 아닌 경우` 와 `주문이 존재하지 않는 경우` 를 한 박스에 그렸다.
두 응답이 구별되지 않아야 남의 주문이 있다는 사실이 새어나가지 않는다. 이미 구현돼 있었다.

---

## 12. 고객 상품 목록 — 유스케이스 21

```
GET /api/v1/products?brandId=&sort=&page=&size=
```

### 정한 것

| 항목 | 결정 |
| --- | --- |
| 정렬 | `latest` · `price_asc` · `likes_desc`. 기본은 `latest` |
| 동률 보조 기준 | **`id` 내림차순** |
| 잘못된 정렬 값 | `400 BAD_REQUEST`. 사용 가능한 값을 메시지에 넣는다 |
| 페이징 | `page` 는 0부터, 기본 크기 20, 최대 100 |

동률 보조 기준이 없으면 페이지를 넘길 때 순서가 달라져 **같은 상품이 두 번 나오거나 빠진다.**
가격이 같은 상품 두 개로 테스트를 걸어 두었다.

### `likes_desc` 만 조인이다

좋아요 수는 관계에서 센다. 서브쿼리로 정렬하려 했으나 QueryDSL 의 `JPAExpressions` 에는
`.desc()` 가 없어 `left join` + `groupBy` 로 바꿨다. 좋아요가 하나도 없는 상품도 나와야 하므로
inner join 이 아니라 left join 이다.

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 목록·필터·정렬·페이징 전체 | E2E 16건 전부 실패 | `ProductSort` · `ProductSearch` · `ProductQueryDslRepository` |
| 2 | 동률은 `id` 내림차순으로 가른다 | 위와 같음 | 모든 정렬에 `product.id.desc()` 를 뒤에 붙임 |
| 3 | 잘못된 정렬 값은 거절한다 | 위와 같음 | `ProductSort.from` 에서 `BAD_REQUEST` |

---

## 13. 내 좋아요 목록 — 유스케이스 22

```
GET /api/v1/users/{userId}/likes
```

### 삭제된 상품을 거절하지 않고 제외한다

그림에는 `상품이 존재하지 않는 경우` 도 거절로 있었지만 빼고 제외로 바꿨다.
거절하면 좋아요해 둔 상품이 삭제되는 순간 그 사용자는 목록을 볼 수 없게 되고,
목록을 못 보니 그 좋아요를 취소할 수도 없다. 스스로 빠져나올 방법이 없어진다.

과제 명세도 "삭제된 상품은 내 목록에서 제외" 로 같은 판단이다. 흐름도를 고쳤다.

### 남의 목록은 403 이다

경로의 `{userId}` 와 `X-USER-ID` 가 다르면 거절한다. 주문에서 남의 주문을 404 로 숨긴 것과
다른데, 요청자가 자기 id 를 이미 알고 있어 숨길 것이 없기 때문이다.

### 검증

`./gradlew :apps:commerce-api:test` — 전체 통과 (ArchUnit 포함)
`./gradlew :apps:commerce-api:ktlintCheck` — 통과

### 남은 것

- **고객 브랜드 목록** — 명세에 없다. 다만 상품 목록의 브랜드 필터를 쓰려면 고객이 `brandId` 를
  알아야 하므로, 실제로 돌려 보려면 필요해진다.
- **쿠폰 · PG** — 유스케이스 1번의 선택 단계이며 범위 밖이다.

---

## 14. 코드 리뷰 대응

`/code-review` 로 10건을 받아 6건을 고치고 4건은 이유를 적고 남겼다.
리뷰 결과를 그대로 옮기지 않고 10건을 코드에서 직접 확인했다. 2~10번은 전부 사실이었다.

### 고친 것

| # | 무엇이 문제였나 | 어떻게 고쳤나 |
| --- | --- | --- |
| 1 | `DELETE /api/v1/orders/{orderId}` 가 **없었다** | `OrderFacade.cancel` 과 컨트롤러 추가. E2E 6건 |
| 5 | `ProductFacade` 에서 목록 하나에 쿼리가 상품 수만큼 늘었다 | 브랜드·좋아요 수를 일괄 조회해 `1 + 2N` → `3` |
| 7 | `OrderRepositoryImpl.find` 만 `deletedAt` 을 거르지 않았다 | `findByIdAndDeletedAtIsNull` 로 통일 |
| 8 | `create` 주석이 "재고를 차감하지 않는다" 인데 차감하고 있었다 | 주석을 실제 동작으로 |
| 9 | 상품 수를 센다는 KDoc 이 `getAll()` 위에 있었다 | `delete()` 위로 |
| 10 | `findByName` 이 죽은 코드였다 | 중복 이름 규칙을 뺄 때 남은 것이라 제거 |

1번이 제일 컸다. `OrderService.cancel` 은 구현돼 통합 테스트까지 통과했는데 **API 가 없어
아무도 부를 수 없었다.** `design.md` 에는 있다고 적혀 있어 문서가 거짓말을 하고 있었다.

도메인과 문서만 보면 완성으로 보였다는 점이 남는다. 계층을 따로 만들면 연결이 빠져도
각 계층의 테스트는 통과한다.

### 남긴 것

| # | 무엇 | 왜 남겼나 |
| --- | --- | --- |
| 2 | 삭제된 상품이면 주문 취소가 `404` | 설계가 더 필요하다. 복원 전용 조회를 둘지, 삭제를 막을지 |
| 3 | DRAFT 주문이 잡은 재고로 상품 삭제 가능 | 관리자 판단 영역으로 본다 |
| 4 | 주문 생성 재고 차감에 락이 없다 | 동시성. 다음 과제에서 다룬다 |
| 6 | 좋아요 동시 등록 시 유니크 제약 위반 → `500` | 위와 같음 |

---

## 15. `OrderItem` → `OrderLine`

유스케이스 20번이 `주문 line` 이라고 적고 있는데 코드만 `OrderItem` 이었다.

```
OrderItem.kt   → OrderLine.kt
order_item     → order_line
Order.items    → Order.lines
응답·요청 JSON  items → lines
```

`OrderService.create` 의 파라미터가 `lines` 와 겹쳐 `commands` 로 바꿨다.
`OrderCommand.Line` 을 받아 `OrderLine` 을 만드는 자리라 이름이 갈린다.

---

## 16. 판매중지 — 브랜드 삭제 설계 변경

이번 회차에서 제일 큰 변경이다.

### 왜 바꿨나

브랜드 삭제가 "삭제되지 않은 상품이 0개일 때만" 이었다. 상품 100개인 브랜드를 내리려면
**API 호출이 201번** 이고(재고 0으로 × 100, 삭제 × 100, 브랜드 1), 지우는 도중에 상품이 하나라도
등록되면 다시 막힌다. 끝나지 않을 수 있는 절차였다.

현실의 브랜드 폐쇄는 상품 수와 무관한 영업 판단이다. 오히려 상품이 있어서 내리는 쪽이 많다.
그래서 **상품이 있어도 삭제하고 대신 그 상품을 판매중지로 바꾸는** 쪽으로 옮겼다.

### 네 가지 중 고른 것

| | 브랜드 삭제 시 | 왜 안 골랐나 |
| --- | --- | --- |
| 삭제를 막는다 | 거절 | 폐쇄가 끝나지 않는다 |
| 상품도 함께 삭제 | 연쇄 `deletedAt` | 되돌릴 수 없고 과거 주문의 상품을 못 찾는다 |
| 브랜드 삭제에서 파생 | 조회 시 판단 | 연쇄 `UPDATE` 가 없어 더 싸다. 다만 중지 이유가 늘면 결국 상태가 필요하다 |
| **상태 필드 + 연쇄 변경** | `SUSPENDED` 로 변경 | **고른 것.** 유스케이스 6번 그림이 `상품 = 판매중지 상태로 변경` 이었다 |

처음 질문에서는 "파생" 을 골랐지만 그림이 상태 변경이었다. **흐름도가 기준이므로** 상태 필드로 갔다.
대가는 브랜드 삭제 시 그 브랜드 상품 수만큼 `UPDATE` 가 도는 것이다.

### 노출 범위 — 한 번 뒤집었다

처음에는 **상세만 열어 두는** 쪽으로 만들었다. 공유된 링크가 죽지 않게 하기 위해서였고,
삭제와 다르다는 것이 응답의 `status` · `sellable` 로 드러나게 했다.

그런데 요구가 "브랜드와 연결된 미삭제 상품 전체가 **고객의 사용 대상에서 제외**" 였다.
상세가 열리고 좋아요가 눌리면 제외된 것이 아니다. 그래서 고객 경로 전체를 막는 쪽으로 바꿨다.

| | 목록 | 상세 | 좋아요 등록 | 주문 |
| --- | --- | --- | --- | --- |
| 판매 중 | 보임 | 보임 | 가능 | 가능 |
| 판매중지 | 제외 | `404` | `404` | `404` |
| 삭제됨 | 제외 | `404` | `404` | `404` |

**중지됐다는 사실 자체를 알리지 않는다.** `409` 로 "판매가 중지되었습니다" 를 돌려주면
상품의 존재가 새어나가고, 그것은 사용 대상에서 제외된 상태가 아니다. 남의 주문을 없는 주문과
같은 `404` 로 숨긴 것과 같은 판단이다.

고객에게 판매중지와 삭제는 구별되지 않는다. 차이는 **관리자에게만 드러나고 되돌릴 수 있다는 점**이다.

### 예외 하나 — 내 좋아요 목록

위 표에서 유일하게 빠지는 칸이다. 내 좋아요 목록은 **카탈로그가 아니라 내 기록**이다.

빼는 쪽을 먼저 생각했다. 고객 경로이므로 표를 그대로 따르면 목록에서도 사라진다. 그런데
사라지면 왜 사라졌는지 알 수 없고, **그 좋아요를 취소할 방법도 같이 없어진다.** 좋아요 취소가
상품을 조회하지 않도록 만들어 둔 것과 어긋난다.

그래서 남기고, 대신 응답에만 `sellable` 을 준다. 화면이 이것으로 클릭을 막는다.

```kotlin
// 고객 상품 응답에는 status · sellable 이 없다 — 보이는 것은 전부 판매 중이므로
// 좋아요 목록에만 따로 둔다
data class LikedProductResponse(..., val sellable: Boolean)
```

삭제된 상품은 여기서도 제외한다. 되돌릴 수 없는 것과 되돌릴 수 있는 것의 차이다.
`ProductInfo` 는 `status` · `sellable` 을 들고 있고, 그것을 응답으로 내보낼지는 DTO 가 고른다.

### TDD 기록

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 1 | 생성 직후에는 판매 중이다 | 없음 — 기본값 | 없음 |
| 2 | 판매를 중지하면 `SUSPENDED` 가 되고 멱등하다 | 단위 14건 중 2건 | `Product.suspend` |
| 3 | 상품이 남아 있어도 삭제되고 그 상품이 판매중지가 된다 | 통합 5건 중 3건 | `BrandService.delete` 에서 `suspendAllByBrand` |
| 4 | 다른 브랜드 상품은 판매 중으로 남는다 | 위와 같음 | 브랜드별 조회로 범위를 좁힘 |
| 5 | 판매중지는 목록에서 빠지고 상세는 열린다 | E2E 1건 | 목록 쿼리에 `selling()` |
| 6 | 판매중지 상품 주문은 `409` 이고 재고가 유지된다 | E2E 1건 | `OrderService.create` 가 `getSellable` 사용 |

뒤집은 뒤에 5 · 6번을 다시 썼다.

| # | 규칙 | RED | GREEN |
| --- | --- | --- | --- |
| 5' | 판매중지는 목록에서도 상세에서도 빠진다 | E2E 1건 — 5번의 기대를 반대로 | `getProduct` 가 `getSellable` 사용 |
| 6' | 판매중지 상품 주문은 없는 상품과 같은 `404` 다 | E2E 1건 — `409` 기대를 `404` 로 | `getSellable` 이 `NOT_FOUND` 를 던짐 |
| 7 | 판매중지 상품은 좋아요도 `404` 다 | E2E 1건 | `LikeService.like` 가 `getSellable` 사용 |
| 8 | 내 좋아요 목록에는 남고 `sellable` 이 `false` 다 | E2E 1건 | `LikedProductResponse` 에 `sellable` |

### `get` 과 `getSellable` 을 나눴다

```kotlin
get(id)          상품이 살아있으면 된다    → 관리자
getSellable(id)  판매 중이어야 한다        → 고객 전부
```

`getSellable` 이 **고객 경로의 유일한 기준**이다. 상세·좋아요·주문이 모두 이것을 쓴다.
한 군데만 고치면 세 경로가 같이 따라오고, 고객 경로에 `get` 이 쓰이면 바로 눈에 띈다.

관리자 상세가 고객용 조회를 쓰고 있어 판매중지 상품이 관리자에게도 안 보일 뻔했다.
`ProductFacade.getProductForAdmin` 을 따로 두어 갈랐다.

### 기존 테스트가 뒤집혔다

`BrandDeleteIntegrationTest` 를 다시 썼고 `AdminV1ApiE2ETest` 의 `409` 기대가 성공으로 바뀌었다.
`usecase.md` 6번 흐름도도 거절 경로 하나가 빠지고 판매중지 전환 단계가 들어갔다.

---

## 17. 트랜잭션 경계를 도메인 서비스로

퍼사드와 도메인 서비스 양쪽에 `@Transactional` 이 있었다. 전파가 `REQUIRED` 라 동작에는
문제가 없지만 **경계가 두 군데처럼 보여 어디가 진짜인지 읽히지 않았다.**

한쪽으로 모으기로 하고 **도메인 서비스**를 택했다. 퍼사드에서 걷어냈다.

### 서비스에서 빼 봤을 때 무엇이 깨졌나

먼저 반대 방향(서비스에서 제거)을 시도했더니 통합 테스트 12건이 깨졌다. 깨진 자리가
**더티 체킹에 기대던 곳**을 그대로 보여 주었다.

| 깨진 이유 | 어디 |
| --- | --- |
| 저장이 일어나지 않음 | `BrandService.update·delete`, `ProductService.update·changeStock·delete`, `OrderService.create·cancel` |
| `LazyInitializationException` | `order.lines` 순회 |
| `TransactionRequiredException` | `deleteAllByProductId` |

경계를 서비스로 되돌린 뒤에도 이 셋은 고쳐 두었다. 트랜잭션이 있어서 동작하는 것과
코드가 정직한 것은 다르기 때문이다.

### 저장을 명시적으로 부른다

```kotlin
// 전 — 저장이 어디에도 없다
fun changeStock(id: Long, amount: Int): Product {
    val product = get(id)
    product.increaseStock(amount)
    return product
}

// 후
    return productRepository.save(product)
```

같이 바뀐 것이 하나 더 있다. `OrderService` 가 `product.decreaseStock()` 을 직접 부르던 것을
`productService.decreaseStock` · `restoreStock` 으로 옮겼다. **상품을 저장하는 책임이 상품
쪽에 모였다.**

### 주문 라인을 함께 읽는다

`Order` 와 `OrderLine` 은 한 애그리거트다. 루트를 읽으면 라인도 함께 와야 하므로 `EAGER` 로
바꿨다. 지연 로딩으로 두면 트랜잭션 밖에서 라인을 읽을 수 없고, 애초에 라인 없이 의미를
갖는 주문이 없다.

---

## 18. 직접 돌려보기

| 파일 | 무엇 |
| --- | --- |
| `scripts/k6/seed.js` | 브랜드 10개 · 상품 100개 · 주문 3개를 넣는 k6 시드 |
| `http/commerce-api.http` | IntelliJ HTTP Client 용 요청 모음. 거절 케이스 포함 |

8080 이 다른 프로세스에 점유돼 있어 **8081** 로 띄워 시드를 돌렸다. 216개 요청이 전부 성공했고
평균 응답 6.8ms 였다.

스크립트를 쓰면서 규칙이 실제로 확인됐다 — 브랜드 이름 4~8자, 상품 생성 시 재고 0이라 따로
채워야 하는 것, 주문 요청 필드가 `lines` 인 것. 문서로만 보던 것들이 요청 한 줄씩으로 드러났다.

`local` 프로파일이 `ddl-auto: create` 라 앱을 재시작하면 시드 데이터가 사라진다.

---

## 19. 브랜드 삭제의 원자성을 테스트로 고정

브랜드 삭제는 상품 전체를 건드린다. 중간에 실패하면 이번 요청의 변경이 전부 취소되어야 하고,
상품마다 커밋하거나 일부만 성공으로 끝내서는 안 된다. **지금 코드는 이미 그렇게 동작한다** —
`BrandService.delete` 하나의 트랜잭션 안에서 다 끝나고 `REQUIRES_NEW` 가 어디에도 없다.

그래서 "지금 맞는지" 를 확인하려고 테스트를 쓴 게 아니다. **나중에 깨질 때 알려 줄 것**이
없어서 썼다. 상품 중지가 배치로 빠지거나 전파가 바뀌면 조용히 반쪽 삭제가 된다.

### 처음 쓴 테스트는 아무것도 잡지 못했다

실패를 `suspendAllByBrand` **안에서** 터뜨렸다. 이러면 전파 방식과 무관하게 전부 롤백되므로
`REQUIRES_NEW` 를 붙여도 테스트가 통과한다. 깨짐을 구분하지 못하는 테스트였다.

```
중지 → [실패]           ← 여기서 터뜨리면 어떤 전파에서도 롤백된다
중지 → 삭제 표시 → [실패]  ← 여기서 터뜨려야 "앞서 중지한 것" 이 되돌아오는지 보인다
```

고친 뒤 `@Transactional(propagation = REQUIRES_NEW)` 를 `suspendAllByBrand` 에 붙여
**실제로 실패하는지 확인했다.** 떼면 통과한다. 그걸 보고서야 테스트가 의미를 가졌다.

### 어떻게 터뜨렸나

`BrandRepository` 를 `@SpykBean` 으로 감싸고 **삭제 표시가 찍힌 브랜드를 저장하는 호출에만**
예외를 던졌다. 같은 `save` 가 생성에도 쓰이므로 인자로 갈라야 했다.

```kotlin
every { brandRepository.save(match { it.deletedAt != null }) } throws
    IllegalStateException("브랜드 삭제 실패")
```

`match` 는 `every { }` 블록 안에서만 쓰는 `MockKMatcherScope` 멤버다. `io.mockk.match` 로
임포트하면 컴파일되지 않는다. 두 번 틀렸다.

검증은 브랜드 `deletedAt` 이 `null` 이고 상품 둘 다 `SELLING` 인지 본다. 하나라도 `SUSPENDED` 로
남으면 반쪽 삭제다.

---

## 아직 없는 것

- **리뷰 2 · 3 · 4 · 6번** — 14절 표 참조
- **동시성** — 다음 과제
- **쿠폰 · PG · 배송 · 사용자 도메인** — 범위 밖
- **고객 브랜드 목록** — 명세에 없다. 상품 목록의 브랜드 필터를 실제로 쓰려면 필요해진다
- **주문 확정 시 상품 재확인** — 명세에 있으나 미구현
