# Outbox 즉시 발행이 poller로만 처리되던 문제

## 요약

알림 등록 요청 이후 `@TransactionalEventListener(AFTER_COMMIT)`에서 실행되는 즉시 발행 경로가 항상 실패하고, 3초 뒤 polling publisher가 대신 처리하고 있었다.
원인은 즉시 발행 경로에서 호출하는 Outbox 조회/상태 변경 서비스가 기본 전파(`REQUIRED`)로 설정되어 있어, `AFTER_COMMIT` 시점의 트랜잭션 컨텍스트를 재사용하다가 JPA 관점에서 활성 트랜잭션이 없는 상태로 쿼리가 실행됐기 때문이다.
두 서비스의 트랜잭션 전파를 `REQUIRES_NEW`로 바꿔 즉시 발행 경로에서 항상 새 트랜잭션을 열도록 수정했다.

## 증상

정상 기대: 요청 스레드에서 즉시 발행 성공 로그가 남는다.

```text
[nio-8080-exec-*] Outbox Kafka 발행 성공. source=immediate ...
```

실제 관측: 요청 스레드 로그는 없고, 3초 뒤 poller 스레드 로그만 남았다.

```text
[scheduling-1]    Outbox Kafka 발행 성공. source=polling ...
```

즉시 발행 실패 warn 로그도 함께 남아 있었다.

```text
Outbox 즉시 발행 실패. polling 재시도 예정. notificationId=..., reason=no transaction is in progress
```

## 재현

같은 시나리오를 `NotificationOutboxPublishTransactionBoundaryTest.publishesOutboxEventImmediatelyAfterNotificationRegisterCommit`로 재현했다.
테스트는 `NotificationRegister.register()`를 호출한 뒤, 요청 스레드 안에서 mock publisher가 정확히 한 번 호출되었는지 확인한다.

### 재현 절차

두 서비스만 fix 커밋 직전 상태로 되돌리고 테스트를 실행한다. 테스트 파일과 나머지 코드는 그대로 둔다.

```bash
git checkout 049a87a~1 -- \
  src/main/java/ogu1208/notificationdeliverysystem/application/notification/NotificationOutboxModifyService.java \
  src/main/java/ogu1208/notificationdeliverysystem/application/notification/NotificationOutboxQueryService.java

./gradlew test --tests \
  "ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxPublishTransactionBoundaryTest.publishesOutboxEventImmediatelyAfterNotificationRegisterCommit"
```

### 관찰된 로그와 결과

`NotificationOutboxModifyService`와 `NotificationOutboxQueryService`가 수정 전 상태(`@Transactional`, `@Transactional(readOnly = true)`)일 때 실제로 관찰된 gradle 출력이다.

```text
WARN  ...NotificationOutboxEventListener :
  Outbox 즉시 발행 실패. polling 재시도 예정.
  notificationId=1, reason=no transaction is in progress

NotificationOutboxPublishTransactionBoundaryTest > 알림 등록 commit 직후 Outbox 이벤트를 즉시 발행한다 FAILED
  org.opentest4j.AssertionFailedError:
  expected: 1
   but was: 0
```

즉시 발행 경로에서 예외가 발생해 publisher가 한 번도 호출되지 않았음을 보여준다.
warn 로그의 `reason=no transaction is in progress`가 “commit 뒤 컨텍스트에서 `REQUIRED`가 outer 트랜잭션 자원을 재사용해 활성 트랜잭션 없이 쿼리를 실행했다”는 원인을 그대로 드러낸다.
이 문서의 원인 분석은 이 로그를 근거로 작성됐다.

### 복원

재현이 끝나면 두 서비스를 fix 상태로 되돌린다.

```bash
git checkout HEAD -- \
  src/main/java/ogu1208/notificationdeliverysystem/application/notification/NotificationOutboxModifyService.java \
  src/main/java/ogu1208/notificationdeliverysystem/application/notification/NotificationOutboxQueryService.java
```

## 원인 분석

### 흐름

즉시 발행 경로의 호출 순서는 다음과 같다.

```text
NotificationRegisterService.register()
  Notification 저장
  OutboxEvent 저장
  outer transaction commit
    -> NotificationOutboxEventListener.publishAfterCommit()   [AFTER_COMMIT phase]
       -> NotificationOutboxPublishService.publishPendingByNotificationId()
          -> NotificationOutboxQueryService.findPendingEventByNotificationId()   [readOnly, REQUIRED]
          -> NotificationOutboxModifyService.markProcessingIfPending()           [REQUIRED]
          -> NotificationMessagePublisher.publish()
          -> NotificationOutboxModifyService.markPublished()                     [REQUIRED]
```

문제는 outer transaction commit 이후에 실행되는 리스너 안에서 다시 DB 작업을 하는 지점이다.

### AFTER_COMMIT의 실제 상태

`@TransactionalEventListener(AFTER_COMMIT)`은 “outer transaction commit이 끝난 뒤”에 실행되지만, Spring의 트랜잭션 후처리 콜백 안에서 실행된다.
이 시점의 상태는 이렇다.

- DB의 outer transaction: commit 완료. 되돌릴 수 없음.
- 커넥션: 스레드에 아직 바인딩된 상태. 곧 반환될 예정.
- `TransactionSynchronizationManager`: 아직 정리되지 않아 스레드에 “commit 끝난 트랜잭션의 흔적”이 남아 있음.
- (JPA 사용 시) `EntityManager`: 아직 열린 채로 남아 있을 수 있음.

즉 DB는 이미 확정됐지만, Spring 관점에서는 여전히 outer transaction의 마무리 블록 안이다.

### 트랜잭션의 두 층: 물리 트랜잭션과 Spring 컨텍스트

이 문제를 정확히 이해하려면 “트랜잭션”이 사실 두 개의 층으로 관리된다는 점을 짚어야 한다.

| 층 | 실체 | AFTER_COMMIT 시점 상태 |
| --- | --- | --- |
| 물리 트랜잭션 | JDBC/JPA가 실제로 여는 DB 세션 (`BEGIN ~ COMMIT`) | 이미 종료됨. commit되어 되돌릴 수 없음. |
| Spring 트랜잭션 컨텍스트 | `TransactionSynchronizationManager`가 스레드에 붙여둔 자원 (`ConnectionHolder`, `EntityManagerHolder`, synchronization 콜백 리스트) | 아직 스레드에 붙어 있음. cleanup 대기 중. |

AFTER_COMMIT 시점의 스레드 상태를 그리면 이렇다.

```text
Thread
  ├─ ConnectionHolder        (outer transaction이 쓰던 커넥션)
  │    └─ JDBC transaction:  COMMIT 이후. 활성 아님.
  ├─ EntityManagerHolder     (outer transaction이 쓰던 EM)
  │    └─ EM transaction:    active = false.
  └─ Synchronization 콜백들   (cleanup 실행 대기)
```

물리 트랜잭션의 알맹이는 이미 죽었지만, “이 트랜잭션이 여기에 있었다”는 스프링 쪽 껍데기(자원 바인딩)는 아직 스레드에 남아 있는 상태다.
이 상태에서 새로 진입하는 `@Transactional` 메서드가 어떻게 판정되는지가 다음 절의 주제다.

### REQUIRED로 인한 재사용

`NotificationOutboxQueryService`, `NotificationOutboxModifyService`는 수정 전에 기본 전파 옵션인 `REQUIRED`를 사용했다.

`REQUIRED`의 규칙은 다음과 같다.

- 현재 실행 중인 트랜잭션이 있으면 그것에 참여한다.
- 없으면 새 트랜잭션을 시작한다.

`AFTER_COMMIT` 안에서 이 판정을 내릴 때, Spring은 스레드에 남아 있는 Spring 트랜잭션 컨텍스트(커넥션 홀더, EM 홀더, synchronization)를 기반으로 판단한다.
껍데기는 있지만 알맹이인 물리 트랜잭션은 이미 죽어 있는 상태에서, `REQUIRED`는 “새 물리 트랜잭션을 열지 말고 기존 컨텍스트에 얹혀서 실행”하는 쪽으로 흐르기 쉽다.
그 결과 실제 쿼리가 실행되는 시점에는 붙을 활성 트랜잭션이 없다.

즉 실패의 구조는 “두 층의 미스매치”다.

- 껍데기 (Spring 컨텍스트): 스레드에 남아 있음 → `REQUIRED`는 “있다”고 판정
- 알맹이 (물리 트랜잭션): 이미 commit되어 죽음 → 쿼리 시점에 활성 트랜잭션 없음

재현 실행에서 실제로 관찰된 `reason=no transaction is in progress`가 바로 이 미스매치를 그대로 드러낸다.
쿼리를 실행할 시점에 JPA/Hibernate가 붙을 수 있는 활성 트랜잭션이 없기 때문에 예외가 던져지고, 리스너의 try/catch가 이를 잡아 warn만 남긴다.
Outbox row는 여전히 `PENDING`으로 남고, 3초 뒤 poller가 잡아간다.
이 원인 진단은 추정이 아니라 “재현” 절에 기록된 실제 gradle 실행 결과와 warn 로그를 근거로 한다.

`REQUIRES_NEW`는 이 판정 자체를 건너뛴다.
스레드에 어떤 껍데기가 남아 있든 상관없이 커넥션 풀에서 새 커넥션을 뽑아 새 물리 트랜잭션을 열기 때문에, 두 층 사이의 미스매치가 생기지 않는다.

## 관련 개념

### `@TransactionalEventListener`의 phase

| phase | 실행 시점 |
| --- | --- |
| `BEFORE_COMMIT` | commit 직전. 아직 rollback 가능한 시점. |
| `AFTER_COMMIT` | commit 직후. 물리 commit은 끝났지만 트랜잭션 자원 정리 중. |
| `AFTER_ROLLBACK` | rollback 직후. 자원 정리 중. |
| `AFTER_COMPLETION` | commit 또는 rollback 이후 최종 완료 시점. |

`AFTER_COMMIT`은 “DB에는 확정됐지만 Spring 내부적으로는 아직 outer transaction의 뒷정리 블록”이라는 점이 핵심이다.
이 안에서 새 DB 작업을 할 때는 기존 트랜잭션 자원을 재사용하지 말고 새 트랜잭션으로 분리해야 안전하다.

### 트랜잭션 전파

| propagation | 의미 |
| --- | --- |
| `REQUIRED` (기본값) | 현재 트랜잭션이 있으면 참여, 없으면 새로 시작. |
| `REQUIRES_NEW` | 항상 새 물리 트랜잭션을 시작. 기존 트랜잭션이 있으면 잠시 suspend. |
| `SUPPORTS` | 있으면 참여, 없으면 트랜잭션 없이 실행. |
| `MANDATORY` | 반드시 기존 트랜잭션이 있어야 함. 없으면 예외. |
| `NOT_SUPPORTED` | 트랜잭션 없이 실행. 있으면 suspend. |
| `NEVER` | 트랜잭션이 있으면 예외. |
| `NESTED` | 저장점(savepoint) 기반 중첩 트랜잭션. |

`REQUIRES_NEW`는 스레드에 어떤 트랜잭션 자원이 붙어 있든 상관없이 항상 새 물리 트랜잭션을 시작하기 때문에, `AFTER_COMMIT` 같은 어정쩡한 컨텍스트에 재사용 없이 독립적으로 실행할 수 있다.

## 해결

즉시 발행 경로에서 호출되는 두 서비스의 트랜잭션 전파를 `REQUIRES_NEW`로 변경했다.

- `NotificationOutboxQueryService`: 클래스 레벨을 `@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)`로 변경.
- `NotificationOutboxModifyService`: 모든 메서드에 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 적용.

수정 후 리스너 흐름은 이렇게 바뀐다.

```text
outer transaction commit
  -> AFTER_COMMIT listener
     -> [REQUIRES_NEW] 새 커넥션, 새 트랜잭션으로 PENDING 조회
     -> [REQUIRES_NEW] 새 트랜잭션으로 PENDING -> PROCESSING 선점
     -> Kafka 발행
     -> [REQUIRES_NEW] 새 트랜잭션으로 PUBLISHED 변경
```

polling publisher는 그대로 REQUIRES_NEW 서비스들을 호출하므로 별도 조정이 필요하지 않다.
polling이 있어야 하는 이유는 그대로 유지된다. 즉시 발행이 어떤 이유로든 실패한 경우와 애플리케이션이 `PROCESSING` 상태에서 종료되는 경우를 보조 경로로 처리해야 하기 때문이다.

## 검증

문서 작성 시 실제로 아래 두 단계를 순서대로 수행해 결과를 확인했다.

### 수정 전 (재현)

두 서비스만 `049a87a~1` 상태로 되돌리고 대상 테스트 실행.

```text
NotificationOutboxPublishTransactionBoundaryTest > 알림 등록 commit 직후 Outbox 이벤트를 즉시 발행한다 FAILED
    org.opentest4j.AssertionFailedError:
    expected: 1
     but was: 0

WARN  ...NotificationOutboxEventListener : Outbox 즉시 발행 실패. polling 재시도 예정. notificationId=1, reason=no transaction is in progress
```

이 로그가 원인 분석의 근거다.

### 수정 후

두 서비스를 fix 상태(`REQUIRES_NEW`)로 되돌리고 동일 테스트 실행.

```text
> Task :test
BUILD SUCCESSFUL in 8s
```

warn 로그가 사라지고 테스트도 통과한다.
전체 테스트도 `./gradlew test`에서 통과함을 fix 커밋 시점에 함께 확인했다.

## 배운 점

- `AFTER_COMMIT` 리스너 안에서 다시 DB 작업을 할 때는 “commit 뒤니까 트랜잭션 문제는 없다”라고 가정하지 않는다.
  Spring은 outer transaction의 후처리 블록 안에 있기 때문에 트랜잭션 자원이 어정쩡하게 남아 있고, `REQUIRED`가 이를 재사용하면 활성 트랜잭션이 없는 상태로 쿼리가 나갈 수 있다.
- 이 계열의 실패는 정상 흐름 로그가 사라지고 보조 경로(여기서는 poller)만 남는 형태로 조용히 드러난다. 정상 경로가 “왜 안 찍히는가”를 별도 테스트로 방어하는 것이 회귀 방지에 유효했다.
- 트러블슈팅 관점에서 `reason=no transaction is in progress`는 “commit 뒤 컨텍스트에서 REQUIRED 재사용”을 강하게 시사하는 시그니처로 기억한다.

## 참고

- 커밋: `049a87a bug: Outbox 즉시 발행 트랜잭션 분리`
- 관련 문서: [`docs/구현내용.md`](../구현내용.md), [`docs/설계판단과고민.md`](../설계판단과고민.md)
- 관련 테스트: `src/test/java/ogu1208/notificationdeliverysystem/application/notification/NotificationOutboxPublishTransactionBoundaryTest.java`
