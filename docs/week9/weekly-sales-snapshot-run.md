# 주간 판매 스냅샷 수동 실행

스케줄러로 매주 돌리거나, 레포 루트에서 gradle 로 잡을 직접 띄운다.

## 스케줄

```bash
./gradlew :apps:commerce-batch:bootRun --args='--job.name=weeklySalesSnapshotJob --spring.batch.job.enabled=false --ranking.schedule.enabled=true'
```

- 배치 앱이 종료하지 않고 떠 있으면서, UTC 월요일 00:30 (KST 09:30) 에 r1 잡을, UTC 월요일 01:30 (KST 10:30) 에 r2 잡을 실행한다.
- 그날 UTC 날짜와 리비전으로 `requestDate` 와 `snapshotId` 를 함께 정한다. 그래서 스케줄 실행에서는 두 값이 어긋나지 않는다.
- `ranking.schedule.enabled` 가 없으면 지금처럼 잡 하나를 돌리고 종료한다.

## 직접 실행

```bash
./gradlew :apps:commerce-batch:bootRun --args='--job.name=weeklySalesSnapshotJob requestDate=2026-11-16,java.time.LocalDate,false snapshotId=2026_11_2_r1'
```

- `requestDate` 의 직전 주(월~일)를 집계한다.
- `snapshotId` 의 리비전으로 r1 과 r2 를 고른다. r2 는 r1 을 먼저 돌린 뒤에 `snapshotId=2026_11_2_r2` 로 띄운다.
- 같은 잡에서 이어서 그 리비전 랭킹을 매기고 공개한다. `snapshotId` 는 아래 표의 `requestDate` 에 맞는 주로 넘긴다. 다른 주를 넘기면 스냅샷 스텝에서 실패한다.

| `requestDate` | 집계 구간 (UTC)         | `snapshot_id`  |
| ------------- | ----------------------- | -------------- |
| `2026-11-16`  | 11/09 00:00 ~ 11/16 00:00 | `2026_11_2_r1` |
| `2026-11-18`  | 11/09 00:00 ~ 11/16 00:00 | `2026_11_2_r1` |
| `2026-11-09`  | 11/02 00:00 ~ 11/09 00:00 | `2026_11_1_r1` |

- local 프로필로 `localhost:3306/loopers` 에 붙는다.

## 다시 실행할 때

- 같은 주·같은 리비전으로 다시 띄우면, `requestDate` 날짜가 달라도 같은 JobInstance 라 Spring Batch 가 `JobInstanceAlreadyCompleteException` 으로 거부한다.
- 실패한 실행은 같은 파라미터로 다시 띄우면 멈춘 곳부터 잇는다.

## 결과 확인

```bash
docker exec docker-mysql-1 mysql -uapplication -papplication loopers -e "
SELECT product_id, SUM(quantity) AS sales_quantity
FROM purchase_activity_snapshot
WHERE snapshot_id = '2026_11_2_r1'
GROUP BY product_id
ORDER BY sales_quantity DESC, product_id
LIMIT 10;"
```

## 실행 시각

주 경계가 UTC 라서 한 주는 KST 월요일 09:00 에 끝난다. 그 전에 그날 날짜로 띄우면 끝나지 않은 주를 집계해 마지막 9시간이 빠진다. 이미 있는 리비전은 다시 쓰지 않으므로 빠진 채로 남는다. 스케줄은 UTC 월요일 00:30·01:30 에 돌아서 이 문제가 없다. 직접 실행할 때만 주의한다.

## 나중에 다룰 것

- 늦은 구매와 r2 는 만들었다. 기준은 `ranking-batch.md` 에 있다. r2 잡이 돈 뒤에 저장된 전주 구매는 반영하지 않고, 수기 등록을 나중에 검토해 볼 수 있다.
- 스케줄 실행이 빠졌을 때. 지금은 매주 월요일 한 번만 돌아서, 그 실행이 빠지면 그 주는 다음 실행에서 채워지지 않는다. 매일 돌리는 방식은 아직 정하지 않았다.
