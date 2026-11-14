# 캠페인 테이블 정의

### campaign

| 컬럼           | 타입           | 제약               | 내용                              |
| -------------- | -------------- | ------------------ | --------------------------------- |
| `id`           | `bigint`       | PK, auto_increment |                                   |
| `name`         | `varchar(100)` | not null           | 캠페인 이름                       |
| `issuable`     | `boolean`      | not null           | 발급 가능 여부                    |
| `total_limit`  | `int`          | not null           | 총 발급 한도                      |
| `issued_count` | `int`          | not null           | 현재 발급 수                      |
| `start_date`   | `datetime(6)`  | not null           | 쿠폰 기간 시작                    |
| `end_date`     | `datetime(6)`  | not null           | 쿠폰 기간 끝                      |
| `created_at`   | `datetime(6)`  | not null           | BaseEntity                        |
| `updated_at`   | `datetime(6)`  | not null           | BaseEntity                        |
| `deleted_at`   | `datetime(6)`  |                    | BaseEntity, null 이면 삭제 안 됨  |
