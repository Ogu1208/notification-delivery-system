# 설계 판단과 고민

## 1. 단순 Async 대신 Kafka를 사용한 이유

처음에는 API 요청을 받은 뒤 내부 thread pool에 발송 작업을 넘기는 방식도 생각할 수 있다.
하지만 이 방식은 서버가 재시작되거나 죽으면 메모리에 있던 작업이 사라진다.

알림 발송은 요청 이력과 처리 상태를 추적할 수 있어야 한다.
그래서 작업을 메모리에만 두지 않고 DB와 Kafka에 남기는 방향을 선택했다.

Kafka는 메시지를 디스크 기반 로그로 저장하고 Consumer가 나중에 이어서 처리할 수 있다.
또 Consumer 수를 늘려 발송 처리량을 확장하기 쉽다.

## 2. Outbox를 추가한 이유

DB 저장 후 Kafka를 바로 호출하면 다음 문제가 생긴다.

```text
DB 저장 성공
Kafka 발행 실패
```

이 경우 알림 요청은 DB에 남았지만 Kafka에는 발송 작업이 없을 수 있다.
운영 관점에서는 이 불일치를 명확히 다뤄야 한다.

그래서 `Notification`과 `OutboxEvent`를 같은 DB 트랜잭션에 저장한다.
API 응답 조건도 Kafka 발행 완료가 아니라 `DB 저장 + Outbox 저장`으로 잡았다.

## 3. 즉시 발행과 polling을 함께 둔 이유

Outbox를 polling만으로 처리하면 구현은 단순하다.
하지만 매 주기마다 DB를 조회하고, 발행 지연도 polling interval에 묶인다.

현재 구현은 commit 이후 즉시 발행을 한 번 시도한다.
평상시에는 Kafka 발행 지연을 줄이고, 실패하면 Outbox 이벤트를 `PENDING`으로 남겨 polling이 재처리한다.

```text
commit 성공
 -> 즉시 발행 시도
 -> 실패 시 PENDING 유지
 -> polling 재처리
```

이 방식은 구현 복잡도를 크게 늘리지 않으면서, polling만 사용하는 방식보다 지연을 줄일 수 있다.

## 4. Outbox 조회 성능

Outbox는 계속 쌓일 수 있다.
단순히 `status = PENDING`만 조회하면 데이터가 많아질수록 느려질 수 있다.

그래서 polling 조회는 다음 형태로 제한했다.

```text
where status = PENDING
order by id asc
limit batchSize
```

이를 위해 `status, id` 복합 인덱스를 둔다.
commit 이후 즉시 발행은 특정 알림 ID의 `PENDING` Outbox 이벤트를 찾으므로 `aggregateId, status` 인덱스도 둔다.

아직 남은 고민은 있다.

- 발행 완료된 Outbox를 계속 `PUBLISHED`로 보관할지
- 일정 기간 후 삭제 또는 archive할지
- publisher가 여러 대 뜰 때 같은 row를 중복 처리하지 않도록 `PROCESSING` 상태나 `SKIP LOCKED`를 둘지

## 5. requestId와 notificationId의 역할

`requestId`는 클라이언트 retry를 식별하기 위한 값이다.
같은 요청을 다시 보낸 경우 새 알림을 만들지 않기 위해 사용한다.

`notificationId`는 서버가 만든 알림 식별자다.
Kafka 메시지 key와 Consumer 처리 대상 식별자로 사용한다.

두 값의 역할은 다르다.

| 값 | 사용 위치 | 목적 |
| --- | --- | --- |
| `requestId` | API 등록 | 같은 클라이언트 요청 중복 방지 |
| `notificationId` | Kafka / Consumer | 저장된 알림 처리 대상 식별 |

## 6. Kafka record key

현재 Kafka record key는 `notificationId`다.
Consumer가 처리할 대상이 DB에 저장된 단건 알림이기 때문이다.

검토한 후보는 다음과 같다.

| 후보 | 장점 | 한계 |
| --- | --- | --- |
| `notificationId` | 처리 대상과 일치하고 분산이 비교적 고르다. | 사용자 단위 순서는 보장하지 않는다. |
| user ID | 사용자별 순서 보장이 쉽다. | 특정 사용자 트래픽이 많으면 hot partition 가능성이 있다. |
| channel | 채널별 격리가 쉽다. | 채널 수가 적어 분산 효과가 낮다. |
| random/null | 분산이 쉽다. | 재처리 추적과 순서 보장이 약하다. |

이번 범위에서는 단건 알림 처리가 중심이라 `notificationId`를 선택했다.

## 7. 단건 알림부터 구현한 이유

그룹/대량 발송을 먼저 넣으면 설계 범위가 크게 늘어난다.

- 수신자 목록 관리
- 그룹 상태
- 전체 건수, 성공 건수, 실패 건수 집계
- Consumer 동시 처리 시 카운트 충돌 방지

이번 과제에서는 단건 알림 흐름을 끝까지 완성하는 데 집중했다.
대량 발송이 필요해지면 `NotificationGroup`을 별도 애그리거트로 추가하는 방향이 낫다고 판단했다.

## 8. 현재 남은 과제

- Redis 분산락으로 Consumer 동시 중복 발송 방지
- Outbox retry count와 실패 사유 저장
- 발행 완료 Outbox 정리 정책
- 여러 Outbox publisher 실행 시 row 선점 전략
- 실제 외부 API 연동 시 timeout, rate limit, retry 정책
- API 서버와 Worker 서버의 물리적 분리
