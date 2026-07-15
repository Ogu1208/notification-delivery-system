# NotificationDeliverySystem

알림 요청을 빠르게 접수하고 실제 발송은 비동기로 처리하는 알림 발송 시스템 과제입니다.

## 현재 구현 범위

현재 구현은 기본 요청 저장 흐름, Outbox 기반 Kafka 발행, Kafka Consumer, 채널별 Mock Sender 연결까지 포함합니다.

- `POST /notifications`
- 알림 도메인 모델과 상태 전이
- `requestId` 필수 검증
- 같은 `requestId` 재요청 시 기존 알림 반환
- H2/JPA 저장과 `requestId` 유니크 제약
- 알림 저장과 같은 트랜잭션에서 Outbox 이벤트 저장
- Outbox polling publisher가 단건 알림 발송 topic(`single-notification-send`)으로 Kafka 메시지 발행
- Kafka Consumer가 단건 알림 메시지를 소비해 발송 유스케이스 호출
- EMAIL, SMS, KAKAO 채널별 Mock Sender 로그 발송
- Kafka 연결 전 단계의 fallback 메시지 발행 대역

Redis 분산락은 이후 단계에서 연결합니다.

그룹/대량 발송은 현재 범위에서 제외합니다.
이후 필요하면 `NotificationGroup`을 추가합니다.
단건 `Notification`은 개별 수신자와 발송 상태만 책임지도록 확장합니다.

## 실행 역할 분리 방향

과제 구현은 단일 Gradle 모듈과 단일 Spring Boot 애플리케이션으로 시작합니다.
운영 환경에서는 같은 코드베이스를 API 서버와 Worker 서버로 나누어 실행할 수 있게 property 기반 활성화 방식을 사용합니다.

```yaml
notification:
  outbox:
    publisher-enabled: false
    polling-interval-ms: 1000
  role:
    api-enabled: true
    worker-enabled: false
```

- API 서버: Web API, DB 저장, Outbox 저장을 담당합니다.
- Outbox Publisher: Outbox 이벤트를 읽어 Kafka Producer를 호출합니다.
- Worker 서버: Kafka Consumer, Redis 분산락, Mock Sender를 담당합니다.
- 로컬 통합 실행: 두 값을 모두 `true`로 두고 한 프로세스에서 전체 흐름을 확인할 수 있습니다.

현재 단계에서는 Consumer bean에 `notification.role.worker-enabled=true` 조건을 적용했습니다.
Outbox polling publisher는 `notification.outbox.publisher-enabled=true`일 때 활성화됩니다.

멀티모듈은 현재 범위에서 제외합니다.
대신 `domain`, `application`, `adapter` 경계를 유지합니다.
이후 `core`, `api-app`, `worker-app` 형태로 분리하기 쉽게 하기 위해서입니다.

## Kafka 처리 판단

Kafka record key는 `notificationId`를 사용합니다.
Consumer가 처리할 대상이 DB에 저장된 단건 알림이기 때문입니다.

현재 API 응답 조건은 DB에 알림과 Outbox 이벤트를 저장하는 것입니다.
Kafka 발행은 Outbox polling publisher가 별도로 수행합니다.

Outbox publisher 내부의 Kafka 발행은 `KafkaTemplate.send(...).join()`으로 완료를 확인한 뒤 Outbox 이벤트를 `PUBLISHED`로 변경합니다.

`requestId`는 클라이언트 retry 중복 방지에 사용합니다.
같은 `requestId` 요청은 새 알림을 만들지 않고 기존 알림을 반환합니다.

이미 `SENT` 또는 `FAILED`인 알림이 Kafka에서 다시 전달되면 발송을 건너뜁니다.
이는 오류가 아니라 Kafka at-least-once 재전달을 고려한 멱등 처리입니다.

`NotificationModifyService`는 `ExternalNotificationSender` 포트에만 의존합니다.
실제 구현체는 `MockNotificationSenderRouter`입니다.
Router가 알림 채널을 보고 EMAIL, SMS, KAKAO Sender 중 하나를 선택합니다.

## API

### 알림 요청 등록

```http
POST /notifications
Content-Type: application/json
```

```json
{
  "requestId": "req-001",
  "receiver": "user@example.com",
  "channel": "EMAIL",
  "title": "welcome",
  "content": "hello"
}
```

응답은 요청이 접수되었음을 의미하는 `202 Accepted`입니다.

```json
{
  "notificationId": 1,
  "status": "PENDING",
  "requestId": "req-001"
}
```

`requestId`, `receiver`, `channel`, `title`, `content`가 누락되거나 비어 있으면 `400 Bad Request`와 `ProblemDetail` 응답을 반환합니다.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "요청 ID는 필수입니다",
  "instance": "/notifications",
  "timestamp": "2026-07-15T16:40:00.000000",
  "exception": "MethodArgumentNotValidException"
}
```

## 테스트

```bash
./gradlew test
```
