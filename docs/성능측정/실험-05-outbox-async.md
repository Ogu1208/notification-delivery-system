# 실험 05 — Outbox listener 비동기 분리 (D 적용)

실험-03에서 확인한 근본 병목(sync outbox listener가 원 요청 스레드의 Hikari 커넥션 점유를 늘리는 구조)을 `@Async` + 전용 Executor로 제거. 요청 응답 흐름과 Kafka 발행을 분리해 pool 소진 경로 자체를 차단.

배경 개념과 실행 절차는 [방법론.md](방법론.md) 참고. 관련 실험은 [실험-03-httpclient5.md](실험-03-httpclient5.md) 참고.

---

## 1. 배경

### 1.1 실험-03에서 확인한 취약점

실험-03의 붕괴 경로를 재정리하면 다음과 같다.

```
[요청 등록 트랜잭션]         → Hikari 커넥션 #1 잡음 → commit → 반납
                                              ↓ AFTER_COMMIT 트리거 (sync)
[Outbox listener sync 실행]  → Hikari 커넥션 #2 잡음 → Kafka publish 대기...
                             → Kafka publish 완료 후 반납
```

원 요청 스레드(Tomcat)가 커넥션 #2까지 잡은 채로 Kafka publish 완료를 대기. Kafka publish에 지연이 발생하면 커넥션 #2 점유 시간이 늘어나 Hikari pool 20개가 소진되고, 이후 요청은 30초 대기 후 timeout으로 cascade 확산.

HC5 도입 시 이 취약점이 노출되어 실험-03에서 반복 붕괴가 발생. 격리 C(bean scope 격리)로 empirical 해결책은 확보했으나 mechanism은 미확정 상태로 남음.

### 1.2 해결 방향

`@TransactionalEventListener(AFTER_COMMIT)`를 `@Async`로 별도 스레드에서 실행. 원 요청 스레드는 커넥션 #1만 잡고 즉시 리턴하고, Kafka publish는 background executor에서 처리.

기대 효과:
- 요청 응답 시간과 Kafka publish 소요 시간의 결합 해제
- Hikari 커넥션 소진 경로 자체 차단
- HC5 등 다른 컴포넌트의 미묘한 부하가 app-api를 tip over시키는 여지 제거

---

## 2. 적용 내용

### 2.1 전용 Executor 정의

`OutboxAsyncConfig` 신설. `@EnableAsync` + `ThreadPoolTaskExecutor` bean.

```java
@Configuration
@EnableAsync
@ConditionalOnProperty(name = "notification.outbox.publisher-enabled", havingValue = "true")
public class OutboxAsyncConfig {

    public static final String EXECUTOR_BEAN_NAME = "outboxAsyncExecutor";

    @Bean(name = EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor outboxAsyncExecutor(...) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("outbox-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
```

### 2.2 크기 근거

- **Max 20 = Hikari maximum-pool-size 20과 대칭**. Background 처리도 커넥션 #2를 잡으므로 Hikari와 동일한 상한
- **Core 8**: 상시 유지 스레드. Kafka publish 정상(5~10ms) 시 800~1600 TPS 이론 처리 능력, 100 TPS 부하에 여유 충분
- **Queue 500**: 순간 spike 흡수용. Kafka publish 지연(300ms 가정) × 100 TPS × 5초 = 150 태스크 규모 흡수 가능

### 2.3 Rejection Policy

`AbortPolicy` 채택. 근거:
- Reject된 이벤트는 DB에 PENDING 상태로 남고, `NotificationOutboxRecoveryPoller`가 주기적으로 재발행 → 데이터 손실 없음
- Listener가 예외를 catch해 warn log 출력 → 관측성 확보
- `CallerRunsPolicy`는 sync 회귀라 이번 실험 목적(async 분리)에 위배
- `DiscardPolicy` / `DiscardOldestPolicy`는 관측이 조용해지거나 순서가 뒤엎여 부적합

### 2.4 Listener 변경

```java
@Async(OutboxAsyncConfig.EXECUTOR_BEAN_NAME)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void publishAfterCommit(NotificationOutboxSavedEvent event) {
    ...
}
```

### 2.5 테스트 처리

`NotificationOutboxPublishTransactionBoundaryTest`는 commit 직후 발행 검증이 목적이므로, `@TestConfiguration`에서 `outboxAsyncExecutor` bean을 `SyncTaskExecutor`로 override해 sync로 실행. Async 스케줄링 검증이 아닌 로직 검증 유지.

---

## 3. 부하 테스트 결과

### 3.1 실행 조건

- 컨테이너 완전 down/up (cold start)
- Kafka topic `single-notification-send` 6 partition으로 재생성
- DB TRUNCATE
- Sendmock RANDOM 모드
- HC5는 실험-03의 격리 C 상태 유지 (worker 전용 bean)
- k6 `notification.load-random` 100 TPS, 1분 warmup + 5분 steady + 30초 rampdown

### 3.2 k6 결과 (steady 구간)

| 지표 | 격리 A (Simple, sync) | 격리 C (HC5 격리, sync) | **실험-05 (async)** |
|---|---|---|---|
| p95 응답시간 | 11.97 ms | 12.65 ms | **7.58 ms** |
| p99 응답시간 | 33.59 ms | 37.22 ms | 44.6 ms |
| 실패율 | 0.00% | 0.00% | **0.00%** |
| 처리 요청 (steady) | 30,001 | 29,999 | **30,000** |
| 실제 TPS (steady) | 100 | 100 | **100** |
| 최대 VU 사용 | 8 | 8 | **7** |

### 3.3 서버 관측 (Prometheus)

**대시보드 스크린샷**: `docs/성능측정/screenshots/`
- `load-random-outbox-async-app.png` — spring-app 대시보드 (API TPS/응답시간, JVM, Hikari, GC)
- `load-random-outbox-async-domain.png` — Outbox publish latency / pending 백로그 / 상태 전이
- `load-random-outbox-async-kafka.png` — Consumer lag / producer 처리량
- `load-random-outbox-async-postgres.png` — Connection, TPS, buffer cache
- `load-random-outbox-async-redis.png` — 명령 처리량 / latency



| 지표 | 값 | 이전 실험-03 (sync) 상황 |
|---|---|---|
| Hikari active max (api) | **6 / 20** (30%) — 대부분 시간 0~1, 순간 spike로 6 도달 | 20 / 20 (소진) |
| Hikari **pending max (api)** | **0** (한 번도 대기 없음) | 22 (대기 폭발) |
| outboxAsyncExecutor active max | 8 (core 값 도달) | (없음) |
| outboxAsyncExecutor queued max | 1 (queue 500 대비 거의 안 씀) | - |
| notification.outbox.publish p95 (immediate) | 5.98 ms | (요청 스레드에서 대기) |
| notification.outbox.publish p99 (immediate) | 21.6 ms | (요청 스레드에서 대기) |

핵심 증거는 **pending이 0으로 완전 평평**한 것. Active 값 자체보다 "pool 소진으로 대기가 발생했느냐"가 안정성의 결정적 지표.

---

## 4. 해석

### 4.1 sync 제거의 효과가 지표로 명확

요청 p95 개선폭(12.65 → 7.58 = **-5 ms**)이 Outbox publish p95(**5.98 ms**)와 거의 정확히 일치. Async가 offload한 Kafka publish 소요 시간만큼 응답이 빨라진 것. **인과 관계가 지표로 재현됨**.

### 4.2 Hikari 소진 경로 원천 차단

이전 실험-03에서는 Hikari 20개가 소진되고 22개 스레드가 대기 → 30초 timeout → cascade. 실험-05에서는 active가 대부분 0~1이고 순간 spike로도 6까지밖에 안 도달, pending은 한 번도 대기 발생 없이 계속 0 유지. **요청 스레드가 커넥션 #2를 잡지 않으므로 pool 소진 자체가 발생하지 않는다**.

### 4.3 Executor 크기 검증

Active max가 8(core 값), queued max 7. Kafka publish 정상(5~10ms) 상황이라 max 20까지 확장할 필요 없이 core만으로 소화. Queue 500은 대부분 비어있어 spike 흡수 여유 큼. 현재 부하에 대해선 **core=4 정도로도 충분**했을 것으로 보이며, max 20은 지연 spike 대비 여유분.

### 4.4 p99가 약간 오른 이유 (추정)

p99가 37.22 → 44.6 ms로 소폭 상승. Async 도입에 따른 tail latency 요인이 추가된 것으로 추정:

- **Async 스케줄링 오버헤드**: `@Async` proxy가 매 호출마다 `executor.submit()` 호출 → task 객체 생성 → queue 삽입. 마이크로초 단위지만 tail에서 튈 여지
- **Queue lock 경합**: 100 TPS로 여러 스레드가 동시에 submit할 때 queue 내부 lock에서 순간 대기
- **GC pause 빈도 증가**: 스레드 수 증가와 task 객체 생성 증가로 young generation GC 잦아짐
- **Thread context switch**: async 워커 스레드 활동으로 context switch 증가

다만 1회 실행 결과라 노이즈 가능성 배제 못함. 실패율 0%, p95 개선폭이 훨씬 크므로 안정성 손상은 없음.

### 4.5 실험-03의 HC5 mechanism 미스터리와의 관계

실험-03에서 "HC5 bean이 app-api에 존재하는 것 자체가 tip over 원인"이라는 empirical fact를 확인했으나 정확한 mechanism은 미확정으로 남겨두었다. 이번 실험이 근본 병목(sync outbox listener)을 제거했으므로, **실험-06에서 HC5 bean 격리를 해제하고 재부하하면** 정말로 HC5 위치가 무관해지는지 검증할 수 있다. 만약 안정하다면 실험-03의 mechanism 미스터리도 자연 해소되는 셈(근본 병목이 있었기 때문에 HC5가 tip over 방아쇠였을 뿐).

---

## 5. 다음 실험

### 5.1 실험-06 (즉시) — HC5 bean 격리 해제 재실험

- 실험-05에서 근본 병목이 제거되었다면, HC5 bean이 app-api에 있어도 안정할 것으로 예상
- `@ConditionalOnProperty` 제거 후 동일 조건으로 재부하
- 안정하면 실험-03의 mechanism 미스터리 자연 해소 (근본 병목이 있었기 때문에 HC5가 tip over 원인이었을 뿐)
- 여전히 무너지면 HC5 자체 mechanism이 별개로 존재 → 추가 조사 필요

### 5.2 p99 tail latency 개선 후보 (다음 작업)

**전제**: 우선 동일 조건 부하 2~3회 반복해서 p99 44 ms 상승이 재현되는지 확인 (지금은 1회 실행이라 노이즈 배제 안 됨). 재현되면 아래 3개 순서로 검토.

**A. Kafka producer sync → async batch (가성비 최고)**
- 현재 `NotificationMessagePublisher`가 sync 대기 방식일 가능성 → `send(callback)` 방식으로 전환, `linger.ms`로 batch 활성화
- 결과 대기 없이 리턴하고 batch로 처리량도 향상. Outbox publish latency 자체가 급감 (5.98 ms → 1 ms 수준까지 가능)
- 비용: 성공 확인 시점이 async로 이동 → outbox `PUBLISHED` 마킹과 실패 처리 흐름 재작성 필요. 변경 규모 중간
- 효과: 큼 (p95/p99 둘 다 개선, 처리량도 향상)

**B. GC 알고리즘 튜닝 (G1 → ZGC)** — A와 병행 가능
- 지금 G1 (Java 17 기본). ZGC는 최대 pause를 10 ms 미만으로 억제하는 저지연 GC
- p99 tail의 상당 부분이 GC pause에서 오므로 유의미한 개선 기대
- 비용: JVM 옵션 한 줄 (`-XX:+UseZGC`). 처리량은 약간 감소할 수 있어 프로덕션 도입 전 부하 검증 필요
- 효과: 중대

**C. `executor.setPrestartAllCoreThreads(true)` — 마이너 튜닝**
- Core 스레드를 앱 시작 시 미리 생성 (기본은 첫 task 도착 시 lazy 생성)
- 초반 몇 요청의 스레드 생성 오버헤드만 제거. 정상 부하 정착 후에는 무의미
- 비용: 코드 한 줄. 효과: 매우 작음. 리스크도 없으니 가볍게 켜볼 수는 있음

**스코프 밖 (기록만)**: Non-blocking 전면 전환 (WebFlux + R2DBC + Reactor Kafka)은 스레드 대기 구조 자체를 없애 tail latency 크게 개선 가능하지만, 전면 재작성 규모라 이 프로젝트 스코프에서는 오버킬. 학습 목적 별개 트랙에서만 고려.

---

## 6. 참고 파일

- k6 결과 (raw): `perf/k6/results/load-random-outbox-async-20260805-165105.json`
  - `-summary.json` / `-stdout.log` 동일 폴더
- Config: `src/main/java/ogu1208/notificationdeliverysystem/adapter/messaging/outbox/OutboxAsyncConfig.java`
- Listener: `src/main/java/ogu1208/notificationdeliverysystem/adapter/messaging/outbox/NotificationOutboxEventListener.java`
