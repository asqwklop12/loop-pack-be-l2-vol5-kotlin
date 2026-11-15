# 주간 판매 스냅샷 수동 실행

스케줄은 아직 없다. 레포 루트에서 gradle 로 잡을 직접 띄운다.

## 실행

```bash
./gradlew :apps:commerce-batch:bootRun --args='--job.name=weeklySalesSnapshotJob requestDate=2026-11-16,java.time.LocalDate'
```

- `requestDate` 의 직전 주(월~일)를 집계한다.

| `requestDate` | 집계 구간 (UTC)         | `snapshot_id`  |
| ------------- | ----------------------- | -------------- |
| `2026-11-16`  | 11/09 00:00 ~ 11/16 00:00 | `2026_11_2_r1` |
| `2026-11-18`  | 11/09 00:00 ~ 11/16 00:00 | `2026_11_2_r1` |
| `2026-11-09`  | 11/02 00:00 ~ 11/09 00:00 | `2026_11_1_r1` |

- local 프로필로 `localhost:3306/loopers` 에 붙는다.

## 다시 실행할 때

- 같은 `requestDate` 로 다시 띄우면 Spring Batch 가 `JobInstanceAlreadyCompleteException` 으로 거부한다.
- 같은 주의 다른 날짜로 띄우면 잡은 성공하지만, 그 주 스냅샷이 이미 있으므로 아무것도 쓰지 않는다.

## 결과 확인

```bash
docker exec docker-mysql-1 mysql -uapplication -papplication loopers -e "
SELECT product_id, sales_quantity
FROM product_sales_snapshot
WHERE snapshot_id = '2026_11_2_r1'
ORDER BY sales_quantity DESC, product_id
LIMIT 10;"
```

## 실행 시각

주 경계가 UTC 라서 한 주는 KST 월요일 09:00 에 끝난다. 그 전에 그날 날짜로 띄우면 끝나지 않은 주를 집계해 마지막 9시간이 빠진다. 이미 있는 주는 다시 쓰지 않으므로 빠진 채로 남는다. 스케줄을 붙일 때는 KST 월요일 09:00 이후로 둔다.
