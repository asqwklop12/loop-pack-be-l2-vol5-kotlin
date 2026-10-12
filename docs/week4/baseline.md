# 조회 기준선

캐시·인덱스를 넣기 전의 상태를 적어둔다. 나중에 "줄었다"를 말하려면 비교할 숫자가 있어야 한다.

---

## 1. 측정 환경

| | |
| --- | --- |
| DB | MySQL 8.0 (`docker/infra-compose.yml`) |
| 데이터 | 브랜드 100 · 상품 1,000,000 · 좋아요 1,000,180 |
| 시드 | `docs/week4/seed-large.sql` |
| 좋아요 분포 | 상품 절반은 0개, 나머지는 0~4개. 최대 4개 |
| 인덱스 | `product` · `product_like` 모두 `PRIMARY` 하나뿐 |

좋아요 수의 최대가 4라서 `likes_desc` 상위권은 **전부 동점**이다. 동점을 가르는 2차 키가
없으면 페이지마다 순서가 달라진다. 이 데이터에서는 그 상황이 상위 페이지에서 바로 나온다.

## 2. 조회 계약 — 이미 구현돼 있는 것

| | |
| --- | --- |
| 경로 | `GET /api/v1/products` |
| 브랜드 필터 | `brandId` — `where product.brand_id = ?` |
| 정렬 | `latest`(기본) · `price_asc` · `likes_desc` |
| 동점 정렬 | 셋 다 `id DESC` 를 2차 키로 쓴다 |
| 페이지 | `page`(0부터) · `size`(기본 20, 최대 100), offset 방식 |
| 목록 조건 | `deleted_at IS NULL AND status = 'SELLING'` |
| 응답 | `items[]{id, name, price, stock, brandId, brandName, likeCount}` + `page, size, totalCount` |
| 오류 | 정렬 값 오류 · `page < 0` · `size` 범위 밖 → `400` |

없는 `brandId` 는 오류가 아니라 빈 목록이다.

## 3. 요청당 SQL 수 — 4개

`ProductListQueryCountTest` 가 Hibernate `Statistics` 로 센 값이다. 쿼리 수는 데이터 양과
무관해서 소량으로 재도 같다.

| 요청 | SQL 수 |
| --- | --- |
| `?brandId=5&sort=price_asc&page=0&size=20` | 4 |
| `?sort=likes_desc&page=0&size=20` | 4 |

네 개가 무엇인지:

| | 어디서 | 하는 일 |
| --- | --- | --- |
| ① | `ProductQueryDslRepository.search` | 상품 한 페이지 |
| ② | `ProductQueryDslRepository.countBy` | 전체 개수 (`totalCount` 때문에 매번) |
| ③ | `BrandService.getAllByIds` | 브랜드명 |
| ④ | `LikeQueryDslRepository.countByProductIds` | 좋아요 수 |

`likes_desc` 는 ①에서 좋아요를 조인으로 세는데도 ④가 따로 나간다. 정렬에 쓴 수를 응답에
쓰지 않고 다시 세고 있다.

## 4. 쿼리별 소요 시간

`SQL_NO_CACHE` + `SHOW PROFILES`, 각 1회. **③은 재지 않았다.**

| | 쿼리 | 시간 |
| --- | --- | --- |
| ① | `price_asc` + 브랜드 필터, `LIMIT 20` | 259 ms |
| ② | 같은 조건의 `COUNT(id)` | 171 ms |
| ④ | `product_id IN (20개)` 로 좋아요 수 | 111 ms |
| ⑤ | `likes_desc` (브랜드 필터 없음) | **2,611 ms** |

⑤가 ①의 10배다. 100만 행을 조인하고 `GROUP BY` + `ORDER BY COUNT()` 를 하므로 임시 테이블과
filesort 가 함께 걸린다.

④의 111 ms 는 **`uk_like_user_product` 가 아직 있던 시점**의 값이다. 아래 5절에서 그 키를
지웠으므로 다시 재면 달라진다.

## 5. 실행 계획

| 쿼리 | type | key | rows | Extra |
| --- | --- | --- | --- | --- |
| ① `price_asc` | `ALL` | — | 994,525 | `Using where; Using filesort` |
| ④ 좋아요 `IN` | `index` | `uk_like_user_product` | 996,442 | `Using where; Using index; Using temporary` |
| ⑤ `likes_desc` (product) | `ALL` | — | 994,525 | `Using where; Using temporary; Using filesort` |
| ⑤ `likes_desc` (product_like) | `index` | `uk_like_user_product` | 996,442 | `Using index; Using join buffer (hash join)` |

`product` 는 탈 인덱스가 없어 전부 `type: ALL` 이다. `brand_id` · `price` · `status` ·
`deleted_at` · `created_at` 중 아무것도 인덱스가 없다.

④가 `uk_like_user_product` 를 "탔다" 기보다, `product_id` 가 그 키의 선두 컬럼이 아니어서
**탐색에 쓰지 못하고 인덱스 전체를 훑어** 커버링으로 때운 것이다. 선두는 `user_id` 였다.

## 6. 측정 중에 바꾼 것

| | 왜 |
| --- | --- |
| `Like` 엔티티에서 `uk_like_user_product` 선언 제거 | 그 키가 `product_id` 조회를 어설프게 반쯤 커버해서, 인덱스를 고르는 판단이 "있음"도 "없음"도 아닌 중간값에 묻힌다 |
| `local` 프로파일 `ddl-auto: create` → `update` | 재시작할 때마다 100만 건이 날아갔다 |

유니크 키를 지우면서 중복 좋아요를 막는 DB 쪽 보증이 없어졌다. 지금은 애플리케이션 코드가
존재 여부를 먼저 확인하는 것으로만 막는다. 관련 테스트는 그대로 통과한다.

## 7. 아직 정하지 않은 것

- 캐시 범위 — 목록·상세 중 한 경로, 재사용할 필드, 제외할 사용자별 정보
- 변경 책임 — 이 응답을 바꾸는 쓰기 경로 전체, TX 와 캐시 갱신·무효화 경계
- 장애 계약 — Redis 오류의 대기 상한·DB 우회, 허용 신선도·복구 조건
