# 상품 판매 스냅샷 테이블 정의

### product_sales_snapshot

| 컬럼             | 타입          | 제약               | 내용                                                |
| ---------------- | ------------- | ------------------ | --------------------------------------------------- |
| `id`             | `bigint`      | PK, auto_increment |                                                     |
| `snapshot_id`    | `varchar(30)` | not null           | 스냅샷 식별자, `연도_월_주차_r버전` (예 `2026_11_1_r1`) |
| `period_start`   | `datetime(6)` | not null           | 집계 구간 시작                                      |
| `period_end`     | `datetime(6)` | not null           | 집계 구간 끝, 시작보다 늦다                         |
| `product_id`     | `bigint`      | not null           | 상품                                                |
| `sales_quantity` | `bigint`      | not null           | 구간 안 판매 수량, 1 이상                           |
| `created_at`     | `datetime(6)` | not null           | BaseEntity                                          |
| `updated_at`     | `datetime(6)` | not null           | BaseEntity                                          |
| `deleted_at`     | `datetime(6)` |                    | BaseEntity, null 이면 삭제 안 됨                    |

`snapshot_id` 의 주차는 월요일부터 일요일까지를 한 주로 센다. 주는 월요일이 속한 달에 들어가고, 그 달의 n 번째 월요일로 시작하는 주가 n 주차다.

| 주 (월~일)   | `snapshot_id` |
| ------------ | ------------- |
| 10/26 ~ 11/1 | `2026_10_4_r1` |
| 11/2 ~ 11/8  | `2026_11_1_r1` |
| 11/30 ~ 12/6 | `2026_11_5_r1` |

| 이름                                           | 종류   | 컬럼                                            |
| ---------------------------------------------- | ------ | ----------------------------------------------- |
| `uk_product_sales_snapshot_snapshot_product`   | unique | `snapshot_id`, `product_id`                     |
| `idx_product_sales_snapshot_snapshot_quantity` | index  | `snapshot_id`, `sales_quantity desc`, `product_id` |
