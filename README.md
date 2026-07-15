# NotificationDeliverySystem

알림 요청을 빠르게 접수하고 실제 발송은 비동기로 처리하는 알림 발송 시스템 과제입니다.

## 현재 구현 범위

1단계 구현은 Kafka 없이 기본 요청 저장 흐름에 집중합니다.

- `POST /notifications`
- 알림 도메인 모델과 상태 전이
- `requestId` 필수 검증
- 같은 `requestId` 재요청 시 기존 알림 반환
- H2/JPA 저장과 `requestId` 유니크 제약
- Kafka 연결 전 단계의 fallback 메시지 발행 대역

Kafka Producer, Consumer, Redis 분산락, Mock Sender는 이후 단계에서 연결합니다.
그룹/대량 발송은 현재 범위에서 제외합니다. 이후 필요하면 `NotificationGroup`을 추가해 여러 단건 알림을 묶고, 단건 `Notification`은 개별 수신자와 발송 상태만 책임지도록 확장합니다.

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
