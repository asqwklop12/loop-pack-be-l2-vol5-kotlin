# 쿠폰 테이블 정의

### coupon

| 컬럼         | 타입           | 제약               | 내용                              |
| ------------ | -------------- | ------------------ | --------------------------------- |
| `id`         | `bigint`       | PK, auto_increment |                                   |
| `campaign_id` | `bigint`      | not null           | 쿠폰을 발급한 캠페인              |
| `user_id`    | `bigint`       | not null           | 쿠폰을 가진 사용자                |
| `created_at` | `datetime(6)`  | not null           | BaseEntity, 발급 시각             |
| `updated_at` | `datetime(6)`  | not null           | BaseEntity                        |
| `deleted_at` | `datetime(6)`  |                    | BaseEntity, null 이면 삭제 안 됨  |

| 인덱스                         | 컬럼                   | 종류   |
| ------------------------------ | ---------------------- | ------ |
| `uk_coupon_campaign_id_user_id` | `campaign_id, user_id` | unique |
