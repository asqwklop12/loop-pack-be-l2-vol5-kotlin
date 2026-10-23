# PG 호출에서 나오는 예외

서킷이든 벌크헤드든 **무엇을 실패로 세는지**가 설정의 핵심이다.
계약서 S-10 이 그 지점을 가리킨다 — "정상 거절은 실패지만 PG 는 건강하다.
서킷이 이것을 실패로 세면 안 된다."

아래는 전부 실측으로 확인한 것이다. `PgClientTest` 와 부하 측정 로그가 근거다.

## 1. PG 가 아픈 쪽

| 상황 | 예외 | 뿌리 | 메시지 |
| --- | --- | --- | --- |
| 읽기 타임아웃 (POST) | `RestClientException` | `SocketTimeoutException` | `Error while extracting response for type [...]` |
| 읽기 타임아웃 (GET) | `ResourceAccessException` | `SocketTimeoutException` | `I/O error on GET request ...: Read timed out` |
| 연결 거부 (`docker stop`) | `ResourceAccessException` | `ConnectException` | `I/O error on POST request ...: Connection refused` |
| PG 5xx | `HttpServerErrorException` | — | 상태코드 |

**같은 읽기 타임아웃이 두 가지로 온다.** 응답 본문을 꺼내다 끊기면 `RestClientException`,
연결·전송 단계에서 끊기면 `ResourceAccessException` 이다.

`PgClientTest` 가 이 구분을 못 박아 두고 있다.

```
읽기 타임아웃   isInstanceOf(RestClientException) + hasRootCauseInstanceOf(SocketTimeoutException)
                isNotInstanceOf(ResourceAccessException)
연결 거부       isInstanceOf(ResourceAccessException) + hasRootCauseInstanceOf(ConnectException)
```

## 2. PG 는 건강한 쪽

| 상황 | 예외 |
| --- | --- |
| 카드번호 형식 (400) | `HttpClientErrorException.BadRequest` |
| 금액 불일치 (409) | `HttpClientErrorException.Conflict` |
| 조회에 거래 없음 (404) | `HttpClientErrorException.NotFound` — `findByOrderId` 가 잡아 `null` 을 돌려준다 |
| 정상 거절 | **예외가 아니다.** 202 로 접수되고 콜백으로 `FAILED` 가 온다 |

## 3. 우리가 만드는 쪽

| 상황 | 예외 |
| --- | --- |
| 응답 본문 없음 | `IllegalStateException` (`PgClient`) |
| 카드 정보 누락 | `CoreException(BAD_REQUEST)` (`CardPaymentStrategy`) |
| 서킷 열림 | `CallNotPermittedException` |
| 벌크헤드 만석 | `BulkheadFullException` |

## 4. 계층이 설정을 정한다

```
RestClientException
├── ResourceAccessException               연결 거부 · 일부 타임아웃
└── RestClientResponseException
    └── HttpStatusCodeException
        ├── HttpServerErrorException      PG 5xx
        └── HttpClientErrorException      400 · 409 · 404
```

`RestClientException` 하나로 받으면 **4xx 까지 같이 들어온다.**
그래서 `HttpClientErrorException` 을 따로 빼야 하고,
resilience4j 는 ignore 를 record 보다 먼저 적용한다.

```yaml
record-exceptions:
  - org.springframework.web.client.RestClientException
ignore-exceptions:
  - org.springframework.web.client.HttpClientErrorException
```

`IllegalStateException` 과 `CoreException` 은 `RestClientException` 이 아니라서
이 설정으로는 실패로 세지 않는다. 둘 다 PG 건강과 무관하므로 그게 맞다.

## 5. 지금 구별할 장치가 없다

예외는 저렇게 갈리는데 **아무도 보지 않는다.**
`CardPaymentStrategy` 가 그대로 던지고 `ApiControllerAdvice` 가 전부 500 으로 바꾼다.

| 지표 | 상태 |
| --- | --- |
| `http.client.requests` | 없음 — RestClient 가 계측되지 않는다 |
| PG 전용 지표 | 없음 |
| 노출된 actuator | `health` · `prometheus` 뿐 |

그래서 그라파나를 봐도 **PG 가 아픈지 우리 DB 가 아픈지 구별되지 않는다.**
둘 다 "주문 확정 500 증가" 로만 보인다. 지금 밖에서 보이는 신호는 두 개다.

```
로그의 예외 이름         사람이 grep 해야 안다
payment PENDING 적체     쌓이면 PG 가 늦다는 뜻
```

> `management.endpoints.web.exposure.include` 를 `application.yml` 에 적어도 먹지 않는다.
> `spring.config.import` 로 들어오는 `monitoring.yml` 이 나중에 적용돼 덮어쓴다.
> 엔드포인트를 늘리려면 그 공용 모듈을 건드려야 한다.
