# 요구사항

## 목표

알림 발송 요청을 빠르게 접수하고, 실제 발송은 비동기로 처리한다.

API 응답은 외부 발송 완료가 아니라 요청 접수 완료를 의미한다.
요청 이력과 처리 상태는 DB에 남기고, 발송 작업은 Kafka 메시지로 전달한다.

## 기능 요구사항

### 알림 등록

- 클라이언트는 `POST /notifications`로 알림 발송을 요청한다.
- 요청에는 `requestId`, `receiver`, `channel`, `title`, `content`가 필요하다.
- 필수값이 없거나 blank면 `400 Bad Request`로 응답한다.
- 정상 요청은 `202 Accepted`로 응답한다.

### 중복 요청 방지

- 클라이언트 retry는 `requestId`로 구분한다.
- 같은 `requestId` 요청이 다시 들어오면 새 알림을 만들지 않고 기존 알림을 반환한다.
- DB에는 `requestId` unique 제약을 둔다.

### 비동기 발송

- API는 실제 외부 발송 완료를 기다리지 않는다.
- 알림 저장과 Outbox 저장이 끝나면 응답한다.
- Kafka 발행은 트랜잭션 commit 이후 별도 흐름에서 수행한다.
- Kafka Consumer가 메시지를 받아 발송 유스케이스를 실행한다.

### 발송 상태

- 알림은 `PENDING`, `SENDING`, `SENT`, `FAILED` 상태를 가진다.
- Consumer가 발송을 시작하면 `SENDING`으로 변경한다.
- Mock Sender 발송 성공 시 `SENT`로 변경한다.
- 발송 중 예외가 발생하면 `FAILED`로 변경한다.
- 이미 `SENT` 또는 `FAILED`인 알림은 재전달되어도 다시 발송하지 않는다.

## 비기능 요구사항

### 장애 대응

- DB 저장과 Kafka 발행 사이의 불일치 가능성을 줄여야 한다.
- Kafka 발행 실패 시 Outbox 이벤트를 남겨 재시도할 수 있어야 한다.
- Kafka는 at-least-once 전달이므로 Consumer는 중복 메시지를 받을 수 있다.

### 확장성

- API 서버와 Worker 서버는 나중에 분리 실행할 수 있어야 한다.
- Consumer 수를 늘려 발송 처리량을 확장할 수 있어야 한다.
- 채널별 발송 구현은 EMAIL, SMS, KAKAO 외 채널을 추가하기 쉬워야 한다.

## 이번 범위

### 포함

- 단건 알림 등록 API
- H2/JPA 저장
- Kafka Producer / Consumer
- Outbox 기반 Kafka 발행 보강
- 채널별 Mock Sender
- 기본 테스트

### 제외

- Redis 분산락 실제 구현
- 실제 외부 발송 API 연동
- 그룹/대량 발송
- 관리자 기능
- 멀티모듈 전환

## 후속 작업

- Consumer 발송 직전 Redis 분산락 추가
- Outbox retry count, 실패 사유, 정리 정책 추가
- 여러 Outbox publisher 실행 시 row 선점 처리 검토
- 실제 외부 API 연동 시 채널별 rate limit과 retry 정책 추가
- 대량 발송이 필요하면 `NotificationGroup` 애그리거트 추가
