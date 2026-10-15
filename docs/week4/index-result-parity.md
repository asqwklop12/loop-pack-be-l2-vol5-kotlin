# 인덱스 전후 응답 동일성

인덱스를 걸면 빨라지는 것만으로는 부족하다. **같은 요청에 같은 응답이 나와야** 한다.
전후 비교의 `결과` 항목을 재서 확인한 기록이다.

---

## 1. 왜 확인해야 하나

인덱스는 **읽는 경로**를 바꾼다. 없을 때는 전부 읽어 filesort 로 정렬하고,
있을 때는 인덱스 순서대로 읽는다. 정렬 키가 **동점인 행들의 순서**는 두 경로에서
달라질 수 있다.

달라지면 사용자에게는 "새로고침했더니 목록이 바뀌었다" 로 보이고, 페이지를 넘길 때
같은 상품이 두 번 나오거나 아예 빠진다.

## 2. 설계상으로는 안전하다

지금 계약의 정렬이 둘 다 `id` 를 2차 키로 쓴다.

```sql
ORDER BY price ASC, id DESC            -- price_asc
ORDER BY created_at DESC, id DESC      -- latest
ORDER BY COUNT(l.id) DESC, id DESC     -- likes_desc
```

`id` 는 유일하므로 **순서가 완전히 결정된다.** 동점이 남을 수 없고, 어느 경로로
읽든 같은 순서가 나와야 한다.

다만 이것은 추론이다. 재서 확인한 것과는 다르다.

## 3. 측정 방법

```
1. 인덱스가 있는 상태에서 8개 요청의 응답을 파일로 받는다
2. 인덱스 3개를 전부 DROP 한다
3. 같은 8개 요청을 다시 받는다
4. diff
5. 인덱스를 복구한다
```

캐시가 끼면 "지난 순위" 가 섞이므로 **요청마다 Redis 를 비웠다.** 비교 대상이
DB 가 돌려준 결과만 되도록 한다.

뗀 인덱스 — `idx_product_brand` · `idx_product_brand_price` · `idx_like_product`.
`uk_like_user_product` 는 성능 인덱스가 아니라 불변식이므로 남겼다.

## 4. 결과 — 8개 전부 일치

| 요청 | 비교 |
| --- | --- |
| `price_asc` · brandId=1 (선택도 18.00%) | 일치 |
| `price_asc` · brandId=95 (선택도 0.29%) | 일치 |
| `price_asc` · brandId=1 · page=5 | 일치 |
| `price_asc` · 브랜드 없음 | 일치 |
| `latest` | 일치 |
| `latest` · brandId=1 · page=2 | 일치 |
| `likes_desc` | 일치 |
| `likes_desc` · brandId=1 | 일치 |

**다름 0 건.** 바이트 단위로 같다.

고른 조건에 뜻이 있다. 선택도가 18% 인 브랜드와 0.29% 인 브랜드를 같이 넣은 것은
**옵티마이저가 다른 계획을 고를 수 있는 구간**이기 때문이다. 인덱스를 타는 쪽과
풀스캔이 더 싸다고 판단하는 쪽 모두에서 결과가 같아야 한다. 뒤 페이지(page=5)는
offset 이 커질 때 순서가 어긋나면 가장 먼저 드러나는 자리다.

## 5. 캐시는 성격이 다르다

캐시 히트는 **지난 순위**를 돌려준다. 순서가 다른 것이 결함이 아니라 설계다.
대신 지켜야 하는 것은 **필터 계약**이고, 그쪽은 테스트가 막고 있다.

| | |
| --- | --- |
| 담아둔 상품이 판매중지되면 목록에서 빠진다 | `ProductSearchCacheIntegrationTest` |
| 담아둔 상품이 삭제되면 목록에서 빠진다 | 같음 |

순서의 신선도는 TTL 60초로 제한된다. [cache-popular-list.md](cache-popular-list.md) 참고.

## 6. 함께 정리한 것

측정 중 `product (status, deleted_at)` 인덱스가 DB 에 있는 것을 발견했다.
엔티티에 선언이 없어 `ddl-auto` 로 스키마를 다시 만들면 사라지는 상태였다.
선택도가 `status` 86% · `deleted_at` 95% 로 거의 거르지 못해 제거했다.

최종 인덱스 구성은 이렇다.

| 테이블 | 인덱스 | 컬럼 | |
| --- | --- | --- | --- |
| `product` | `PRIMARY` | `id` | UNIQUE |
| `product` | `idx_product_brand` | `brand_id` | 복합의 선두에 포함 — 비교용 |
| `product` | `idx_product_brand_price` | `brand_id`, `price` | 목록 조회가 쓰는 것 |
| `product_like` | `PRIMARY` | `id` | UNIQUE |
| `product_like` | `uk_like_user_product` | `user_id`, `product_id` | UNIQUE — 중복 좋아요 방지 |
| `product_like` | `idx_like_product` | `product_id` | 좋아요 수 집계 |

`idx_product_brand` 는 `idx_product_brand_price` 의 선두에 포함되므로 중복이다.
두 구성을 비교하는 동안만 함께 둔다.
