# PG 시뮬레이터 변경 기록

버전은 `apps/pg-simulator/build.gradle.kts` 의 `version` 한 곳에만 있다.
루트 `build.gradle.kts` 는 버전이 비어 있을 때만 git hash 를 넣으므로, 모듈에서 박은 값이 이긴다.

도는 컨테이너에 직접 물어볼 수 있다.

```
$ curl -s localhost:8082/actuator/info
{"build":{"artifact":"pg-simulator","version":"0.1.0","time":"..."}}
```

산출물 이름은 `pg-simulator.jar` 로 고정돼 있다. 버전을 파일명에 넣으면 Dockerfile 의 COPY 가
매번 바뀌고, 이전 버전 jar 가 `build/libs` 에 쌓여 와일드카드 COPY 가 깨진다.
버전은 파일명이 아니라 `build-info.properties` 로 따라간다.

올리는 기준 — 동작이 바뀌면 minor, 버그 수정이면 patch. 계약(§6 인터페이스)이 깨지면 major.

| 버전 | 날짜 | 바뀐 것 | commerce-api 에 주는 영향 |
| --- | --- | --- | --- |
| 0.1.0 | 2026-10-18 | 첫 구동. 접수(202 PENDING) · 거래키 조회 · orderId 조회 · orderId 멱등 · 지연 후 확정 · 콜백 발신(재발신 없음) · 확률 기반 거절·콜백 드롭 | 없음 (연동 전) |
