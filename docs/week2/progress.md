# 구현 기록 — 2주차

`CLAUDE.md` 의 TDD 규칙(`RED → GREEN → REFACTOR`)에 따라 규칙 하나씩 구현한다.
각 항목의 RED 는 실행해서 확인한 실패다.

## 구현 순서

의존이 없는 것부터 쌓는다. 주문이 마지막인 이유는 앞의 넷이 있어야 확정 흐름을 검증할 수 있어서다.

```
1  브랜드        의존 없음                      ← 진행 중
2  상품          브랜드 참조, 재고 보유
3  좋아요        상품 참조, 관계 모델
4  포인트        의존 없음
5  주문          상품·재고·포인트를 모두 사용
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
