# Week2 발송 보장과 재처리 설계

## 목표

Week2의 목표는 알림 메시지를 실제 Mock Send API까지 전달하고, 성공하지 못한 메시지도 잃어버리지 않고 상태로 추적할 수 있게 만드는 것이다.

실서비스에서 외부 발송 성공률 100%는 보장할 수 없다.
따라서 이 시스템에서 말하는 발송 보장은 모든 알림이 반드시 `SENT`가 된다는 뜻이 아니다.
실패하더라도 실패 사실을 DB 상태로 남기고, 재처리 가능한 실패는 다시 시도하며, 자동 재시도 한도를 넘은 알림은 운영자가 확인할 수 있는 최종 상태로 분리한다는 뜻이다.

Week1은 다음 범위까지 구현했다.

- 알림 발송 등록 API
- `requestId` 기반 중복 요청 방지
- Outbox 기반 Kafka Producer 발행
- Kafka Consumer 기본 연동
- Consumer에서 채널별 Mock Sender 호출 전 단계
- Redis 분산락을 이용한 중복 발송 방지 골격

Week2는 다음 범위를 추가한다.

- Mock Send API 실제 HTTP 연동
- timeout, 4xx, 5xx, 429, 응답 파싱 실패 분류
- 실패 시 재처리 흐름 설계와 구현
- 최대 재시도 초과 시 `DEAD` 상태 분리
- 발송 처리 결과 조회 가능한 DB 상태 확장
- 성능 측정과 제출 문서화

모니터링과 성능 개선은 설계 관점과 측정 항목을 먼저 정리하되, 구현 우선순위는 뒤로 둔다.

## Mock Send API 계약

사용할 Mock API 프로젝트는 로컬의 다음 경로에 둔다.

```text
/Users/ogu/study/과제전형 5기/알림발송시스템/sendmock
```

기본 실행 정보는 다음과 같다.

```text
POST http://localhost:8081/mock/send
```

요청 예시는 다음과 같다.

```json
{
  "requestId": "req-001",
  "channelType": "SMS",
  "receiver": "01012345678",
  "message": "test message",
  "metadata": {
    "notificationId": 1
  }
}
```

성공 응답은 `200 OK`와 함께 다음 형식으로 온다.

```json
{
  "result": "SUCCESS",
  "requestId": "req-001",
  "channelType": "SMS",
  "processedAt": "2026-07-22T00:00:00Z",
  "latencyMs": 120
}
```

실패 응답은 상태 코드와 함께 다음 형식으로 온다.

```json
{
  "result": "FAIL",
  "requestId": "req-001",
  "channelType": "SMS",
  "errorCode": "MOCK_UNAVAILABLE",
  "message": "Mock failure generated intentionally",
  "processedAt": "2026-07-22T00:00:00Z",
  "latencyMs": 350
}
```

Mock API의 기본 설정은 `RANDOM` 모드다.
지연은 25% 확률로 800ms에서 4000ms 사이에서 발생하고, 실패는 10% 확률로 `500`, `503`, `429` 중 하나로 발생한다.

## 상태 모델

Week2에서는 알림 상태를 다음처럼 확장한다.

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 요청 저장 완료, 발송 대기 |
| `PROCESSING` | Consumer가 발송 처리 중 |
| `SENT` | Mock Send API 호출 성공 |
| `RETRY_WAIT` | 일시 실패, 재시도 예정 |
| `FAILED` | 요청 데이터 문제 또는 재시도하지 않는 실패 |
| `DEAD` | 자동 재시도 한도 초과, 운영자 확인 필요 |

현재 코드의 `SENDING`은 Week2 첫 작업에서 `PROCESSING`으로 변경했다.
면접 질문이나 제출 문서에서 "Consumer 처리 중"이라는 의미가 더 명확하고, `RETRY_WAIT`과 함께 읽을 때 상태 전이가 자연스럽기 때문이다.

상태 전이는 다음을 기준으로 한다.

```text
PENDING
 -> PROCESSING
 -> SENT

PROCESSING
 -> RETRY_WAIT
 -> PROCESSING

PROCESSING
 -> FAILED

RETRY_WAIT
 -> DEAD
```

`SENT`, `FAILED`, `DEAD`는 자동 처리 기준의 최종 상태다.
단, `DEAD`는 발송 포기가 아니라 자동 재시도를 멈추고 운영자 확인 흐름으로 전환한 상태다.
추후 관리자 API가 생기면 `DEAD` 알림을 수동 재처리하거나 실패 사유를 확인한 뒤 상태를 변경하는 확장이 가능하다.

## 실패 분류

Mock API 호출 결과는 같은 실패로 처리하지 않는다.

| 결과 | 분류 | 상태 | 재시도 |
| --- | --- | --- | --- |
| `2xx` | 발송 성공 | `SENT` | 없음 |
| `400`, `404` 등 일반 `4xx` | 요청 데이터 문제 가능성 | `FAILED` | 없음 |
| `429` | 외부 API rate limit | `RETRY_WAIT` | 최대 3회 |
| `500`, `503` 등 `5xx` | 외부 API 일시 장애 가능성 | `RETRY_WAIT` | 최대 3회 |
| connect timeout | 네트워크 또는 연결 지연 | `RETRY_WAIT` | 최대 3회 |
| read timeout | 응답 지연 | `RETRY_WAIT` | 최대 3회 |
| 응답 파싱 실패 | 연동 포맷 불일치 | `FAILED` | 없음 |
| 알 수 없는 예외 | 내부 처리 실패 | `RETRY_WAIT` 우선 | 최대 3회 |

일반 `4xx`는 클라이언트 요청 또는 서버가 외부 API에 전달한 데이터가 잘못되었을 가능성이 높으므로 즉시 `FAILED`로 둔다.
다만 `429 Too Many Requests`는 요청 데이터 문제가 아니라 처리량 제한이므로 재시도 가능 실패로 분리한다.

## 재시도 정책

재시도 정책은 짧고 명확하게 시작한다.

| 항목 | 정책 |
| --- | --- |
| 최대 재시도 횟수 | 3회 |
| 재시도 대상 | `429`, `5xx`, connect timeout, read timeout, 알 수 없는 일시 예외 |
| 재시도 제외 | 일반 `4xx`, 응답 파싱 실패 |
| 재시도 대기 | backoff 계산 후 `nextRetryAt` 저장 |
| 초과 처리 | `DEAD` 상태 전환 |

알림에는 다음 필드를 추가한다.

| 필드 | 의미 |
| --- | --- |
| `attemptCount` | 실제 발송 시도 횟수 |
| `retryCount` | 실패 후 재시도를 예약한 횟수 |
| `lastError` | 마지막 실패 사유 요약 |
| `lastErrorCode` | 외부 API error code 또는 내부 분류 코드 |
| `lastExternalStatusCode` | 외부 API HTTP status |
| `lastFailedAt` | 마지막 실패 시각 |
| `nextRetryAt` | 다음 재시도 가능 시각 |

초기 backoff는 다음처럼 둔다.

```text
1회 실패 후: 5초 뒤 재시도
2회 실패 후: 15초 뒤 재시도
3회 실패 후: 45초 뒤 재시도
3회 초과: DEAD
```

정확한 값은 설정으로 분리한다.
문서와 테스트에서는 기본값을 위 값으로 설명한다.

## 재처리 흐름

Week2의 기본 재처리 방식은 DB의 `RETRY_WAIT` 상태와 `nextRetryAt` 기준 polling이다.
WAIT 토픽은 이후 확장 후보로 남긴다.

선택 이유는 다음과 같다.

- 현재 프로젝트는 이미 H2/JPA와 Outbox polling 구조가 있다.
- 재시도 대상 조회를 DB 상태로 설명하기 쉽다.
- 과제 면접에서 실패 상태와 재시도 대상을 DB로 추적한다는 점을 명확히 보여줄 수 있다.
- Kafka delay topic, retry topic, DLQ topic까지 한 번에 넣으면 구현 범위가 커진다.

재처리 흐름은 다음과 같다.

```text
Kafka Consumer
 -> Notification 조회
 -> 최종 상태면 skip
 -> Redis lock 획득
 -> PROCESSING 변경, attemptCount 증가
 -> Mock Send API 호출
 -> 성공: SENT
 -> 일반 4xx 또는 파싱 실패: FAILED
 -> 429, 5xx, timeout: RETRY_WAIT, retryCount 증가, nextRetryAt 저장
 -> retryCount 초과: DEAD
 -> Redis lock 해제
```

`RETRY_WAIT` 상태의 알림은 별도 retry poller가 조회한다.

```text
NotificationRetryPoller
 -> status = RETRY_WAIT and nextRetryAt <= now 대상 제한 조회
 -> Kafka WORK 메시지 재발행
 -> Consumer가 다시 처리
```

retry poller는 같은 큐에 실패 메시지를 즉시 다시 넣어 외부 API 장애를 증폭시키지 않는다.
반복 실패하는 메시지는 `nextRetryAt`까지 대기하고, 최대 횟수를 넘으면 `DEAD`로 분리한다.

## DLQ와 DEAD 상태

Kafka의 DLQ topic을 바로 도입하는 대신, Week2의 1차 구현은 DB의 `DEAD` 상태를 기준으로 한다.
현재 시스템의 핵심 조회 대상은 `Notification`이고, 운영자가 확인해야 하는 정보도 알림 row에 모이는 편이 단순하기 때문이다.

`DEAD` 상태를 두는 이유는 다음과 같다.

- 자동 재시도를 중단해 계속 실패하는 메시지가 시스템 자원을 쓰지 않게 한다.
- 요청 데이터 문제인지 외부 API 장애인지 운영자가 추적할 수 있게 한다.
- 추후 수동 재처리 API 또는 상태 변경 기능으로 확장할 수 있다.

Kafka DLQ topic은 다음 단계의 확장 후보로 둔다.

```text
single-notification-send.DLT
```

DLQ topic을 도입한다면 `DEAD` 상태 저장과 DLQ 발행은 둘 중 하나만 선택하는 것이 아니라 함께 사용할 수 있다.
DB는 운영 조회와 상태 관리의 기준이고, DLQ topic은 메시지 기반 후속 분석이나 별도 복구 워커의 입력으로 쓸 수 있다.

## 중복 발송 방지

Kafka와 Outbox는 at-least-once 구조다.
따라서 같은 `notificationId` 메시지가 Consumer에 두 번 전달될 수 있다.

중복 발송 방지는 다음 순서로 처리한다.

1. DB에서 알림을 조회한다.
2. `SENT`, `FAILED`, `DEAD`면 외부 API를 호출하지 않고 skip한다.
3. Redis `dispatch-lock:{notificationId}` 락을 획득한다.
4. 락을 얻은 Consumer만 `PROCESSING`으로 변경하고 Mock API를 호출한다.
5. 처리 결과를 DB 상태로 저장한다.
6. 락을 해제한다.

Consumer가 발송 후 Kafka offset commit 전에 죽으면 Kafka는 같은 메시지를 다시 전달할 수 있다.
이때 DB가 이미 `SENT`라면 재전달된 메시지는 외부 API 호출 없이 skip된다.

가장 까다로운 경우는 Mock API 호출은 성공했지만 `SENT` 저장 전에 Consumer가 죽는 상황이다.
이 경우 DB에는 성공이 남지 않아 재처리 시 중복 발송 가능성이 있다.
이를 완전히 줄이려면 외부 API가 `requestId` 기반 idempotency key를 지원해야 한다.
Mock API 요청에는 `requestId`를 포함하므로, 실제 외부 API로 바꿀 때도 같은 값을 `Idempotency-Key` 헤더 또는 요청 바디로 전달하는 설계를 유지한다.

## 면접 질문 기준 답변

### Mock API 호출 중 timeout이 나면 이 알림은 어떤 상태가 되나?

connect timeout 또는 read timeout은 외부 API 일시 장애로 보고 `RETRY_WAIT` 상태가 된다.
`retryCount`, `lastError`, `nextRetryAt`을 저장해 이후 retry poller가 다시 Kafka WORK 메시지를 발행한다.
최대 재시도 3회를 초과하면 `DEAD`로 전환한다.

### Consumer가 발송 후 commit 전 죽으면 중복 발송 되지 않나?

Kafka offset commit 전 죽으면 같은 메시지가 재전달될 수 있다.
이 시스템은 이를 정상 가능성으로 보고 DB 최종 상태와 Redis 락으로 방어한다.
이미 `SENT`로 저장된 알림은 재전달되어도 외부 API를 다시 호출하지 않는다.

다만 외부 API 호출 성공 후 `SENT` 저장 전에 죽으면 DB만으로는 성공 여부를 알 수 없어 중복 발송 가능성이 남는다.
이 위험을 줄이기 위해 외부 API에는 `requestId`를 idempotency key로 전달한다.
즉 내부 DB 상태 체크와 외부 API 멱등성 키를 함께 사용한다.

### 재시도는 몇 번까지 하나? 계속 실패하면 어떻게 하나?

재시도 가능한 실패는 최대 3회까지 재시도한다.
대상은 `429`, `5xx`, timeout 같은 일시 실패다.
일반 `4xx`와 응답 파싱 실패는 재시도하지 않고 `FAILED`로 둔다.
3회를 초과하면 `DEAD`로 전환해 자동 재시도를 중단하고 운영자 확인 대상으로 남긴다.

### 현재 구조는 어느 정도 TPS까지 버틸 수 있나?

TPS는 감으로 말하지 않고 측정한다.
이 구조의 병목 후보는 API의 DB 저장과 Outbox 저장, Kafka partition 수, Consumer concurrency, Mock API 응답 시간과 rate limit, DB 상태 업데이트, Redis 락, JVM 리소스다.

초기 성능 검증은 k6 또는 nGrinder로 `POST /notifications` 접수 TPS와 Consumer 처리량을 분리 측정한다.
응답 TPS, Consumer lag, Mock API latency, DB connection 사용량, retry 증가량을 함께 보고 병목 지점을 설명한다.

## 성능 측정 관점

성능 검증은 수치 하나를 얻는 것이 아니라 병목 위치를 찾는 작업이다.

| 구간 | 볼 지표 |
| --- | --- |
| Producer/API | 요청 TPS, 응답 시간 p95/p99, DB 저장 시간, Kafka publish latency |
| Kafka | partition 수, consumer lag, producer request latency, consumer request latency |
| Consumer | concurrency, 처리 시간, 초당 처리 건수, retry 발생 비율 |
| Mock API | 평균 응답 시간, timeout 비율, 5xx 비율, 429 비율 |
| DB | connection 사용량, 상태 update 지연, slow query, lock wait |
| Redis | lock 획득 실패 수, latency, connection 사용량 |
| JVM | CPU, memory, GC pause, thread 상태 |

순서 보장이 필요 없다면 처리량은 다음 방향으로 늘릴 수 있다.

- Kafka partition 증가
- Consumer concurrency 증가
- Consumer 인스턴스 수평 확장
- 외부 API 호출 비동기화
- Java virtual thread 검토

다만 병렬성을 무작정 높이면 외부 API rate limit을 압박하고, retry가 몰리면서 장애를 증폭시킬 수 있다.
partition key 선택에 따라 hot partition이 생길 수 있고, DB 상태 update가 새로운 병목이 될 수도 있다.
따라서 성능 개선은 병목 위치를 측정한 뒤 안전하게 진행한다.

## 모니터링 후보

초기 구현에서는 로그와 DB 상태 조회를 우선한다.
운영 확장 시 다음 도구와 지표를 붙인다.

| 대상 | 지표 |
| --- | --- |
| Application | CPU, memory, full GC, API latency p95/p99, Consumer 처리 시간 |
| Kafka | consumer lag, partition별 메시지 쏠림, producer/consumer request latency |
| External API | timeout 비율, 5xx 비율, 429 비율, 평균 응답 시간 |
| DB | CPU, connection 사용량, 상태 업데이트 지연, slow query |
| Retry | `RETRY_WAIT` 증가량, `DEAD` 증가량, retry 성공률 |

도구 후보는 다음과 같다.

- Grafana, Loki, Tempo, Prometheus
- Datadog APM, Logs
- Elastic Observability, Kibana
- AWS CloudWatch Logs, X-Ray

모든 처리 로그에는 최소한 다음 값을 포함한다.

- `requestId`
- `notificationId`
- `channel`
- `receiver`
- `retryCount`
- 외부 API status code
- elapsed time
- result status

## 구현 순서

모니터링과 성능 개선은 마지막으로 미루고, 발송 결과를 상태로 남기는 흐름부터 구현한다.

1. 도메인 상태와 필드 확장
   - 완료: `SENDING`을 `PROCESSING`으로 변경
   - 완료: `RETRY_WAIT`, `DEAD` 추가
   - 완료: `retryCount`, `lastError`, `lastErrorCode`, `lastExternalStatusCode`, `lastFailedAt`, `nextRetryAt` 추가
2. Mock Send API HTTP adapter 추가
   - 완료: `POST /mock/send`
   - 완료: connect timeout, read timeout 설정
   - 완료: 요청 DTO 분리
   - 완료: 응답 DTO 기반 실패 분류
3. 실패 분류 모델 추가
   - 완료: 성공, 영구 실패, 재시도 가능 실패 구분
   - 완료: `429`는 재시도 가능 실패로 분리
4. 발송 유스케이스 상태 전이 변경
   - 완료: `PROCESSING`
   - 완료: `SENT`
   - 완료: `FAILED`
   - 완료: `RETRY_WAIT`
   - 완료: `DEAD`
5. retry poller 추가
   - 완료: `RETRY_WAIT`와 `nextRetryAt` 기준 조회
   - 완료: Kafka WORK 메시지 재발행
6. 중복 발송 방지 보강
   - 완료: 최종 상태 skip 기준에 `DEAD` 포함
   - 완료: Redis lock TTL이 HTTP timeout보다 길도록 설정
7. 문서와 테스트 정리
   - 완료: 도메인 상태 전이 테스트
   - 완료: 실패 분류 테스트
   - 완료: retry 초과 시 `DEAD` 테스트
   - 완료: Mock API adapter timeout 테스트
8. 성능 측정 문서화
   - k6 또는 nGrinder 측정 시나리오 정리
   - 병목 후보와 개선 방향 정리

## 구현에서 미룰 항목

다음 항목은 Week2 후반 또는 후속 작업으로 미룬다.

- Kafka retry topic과 DLQ topic 실제 구성
- 관리자용 `DEAD` 재처리 API
- Prometheus metric 코드 연동
- Grafana dashboard 구성
- Circuit Breaker 라이브러리 도입
- 외부 API별 세밀한 rate limiter

다만 위 항목을 미루더라도 `RETRY_WAIT`, `FAILED`, `DEAD` 상태와 실패 사유는 DB에 남긴다.
이것이 Week2 발송 보장 설계의 핵심이다.
