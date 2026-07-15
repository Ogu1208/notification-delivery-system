# NotificationDeliverySystem

알림 요청을 빠르게 접수하고 실제 발송은 비동기로 처리하는 알림 발송 시스템 과제입니다.

## 현재 구현 범위

현재 구현은 기본 요청 저장 흐름과 Kafka Producer 연결까지 포함합니다.

- `POST /notifications`
- 알림 도메인 모델과 상태 전이
- `requestId` 필수 검증
- 같은 `requestId` 재요청 시 기존 알림 반환
- H2/JPA 저장과 `requestId` 유니크 제약
- 단건 알림 발송 topic(`single-notification-send`)으로 Kafka 메시지 발행
- Kafka 연결 전 단계의 fallback 메시지 발행 대역

Kafka Consumer, Redis 분산락, Mock Sender는 이후 단계에서 연결합니다.
그룹/대량 발송은 현재 범위에서 제외합니다. 이후 필요하면 `NotificationGroup`을 추가해 여러 단건 알림을 묶고, 단건 `Notification`은 개별 수신자와 발송 상태만 책임지도록 확장합니다.

## 실행 역할 분리 방향

과제 구현은 단일 Gradle 모듈과 단일 Spring Boot 애플리케이션으로 시작합니다.
운영 환경에서는 같은 코드베이스를 API 서버와 Worker 서버로 나누어 실행할 수 있게 property 기반 활성화 방식을 사용합니다.

```yaml
notification:
  role:
    api-enabled: true
    worker-enabled: false
```

- API 서버: Web API, DB 저장, Kafka Producer를 담당합니다.
- Worker 서버: Kafka Consumer, Redis 분산락, Mock Sender를 담당합니다.
- 로컬 통합 실행: 두 값을 모두 `true`로 두고 한 프로세스에서 전체 흐름을 확인할 수 있습니다.

현재 단계에서는 Producer까지 구현되어 있고, Consumer bean 조건 분리는 Consumer를 추가하는 단계에서 적용합니다.
멀티모듈은 현재 범위에서 제외하지만 `domain`, `application`, `adapter` 경계를 유지해 이후 `core`, `api-app`, `worker-app` 형태로 분리할 수 있게 합니다.

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
