# 실험 03 — Apache HttpClient 5 도입 시도 (실패, 롤백)

코드리뷰와 수업 내용을 반영해 `SimpleClientHttpRequestFactory`(JDK `HttpURLConnection` 기반) 대신 Apache HttpClient 5 + `HttpComponentsClientHttpRequestFactory`로 전환을 시도했으나, RANDOM 부하 하에서 API 계층이 반복적으로 붕괴됨. 격리 실험으로 HttpClient 5 도입이 직접 원인임을 확인하고, 원인 규명 전 롤백 결정.

배경 개념과 실행 절차는 [방법론.md](방법론.md) 참고. 관련 실험은 [실험-02-튜닝v1.md](실험-02-튜닝v1.md) 참고.

---

## 1. 배경

### 1.1 코드리뷰 지적

```
4. 외부 API 커넥션 풀이 없습니다
SimpleClientHttpRequestFactory는 JDK 기본 HTTP 연결을 쓰기 때문에
커넥션 풀 크기를 지정할 수 없고, 대상 호스트당 5개로 묶입니다.

동시 발송이 그보다 많아지면 매 호출마다 연결을 새로 맺습니다.
build.gradle.kts에 HTTP 클라이언트 의존성이 없어 기본 구현으로 잡히신 것 같은데,
Apache HttpClient 5를 추가하고 풀 크기를 명시하시는 걸 권장드립니다.
```

### 1.2 JDK KeepAliveCache의 제약

- `SimpleClientHttpRequestFactory`는 내부적으로 JDK `HttpURLConnection` 사용
- JDK 내부 `KeepAliveCache`의 host별 재사용 커넥션 상한이 기본 5개 (`http.maxConnections` JVM 시스템 프로퍼티)
- 동시 요청이 5개를 초과하면 매번 새 TCP 연결이 필요해져 오버헤드 발생
- 앞단에서 병렬 요청 수를 늘리는 순간 이 상한이 병목이 됨
- 해결책은 `HttpComponentsClientHttpRequestFactory`로 Apache HttpClient 5를 붙여 pool 크기를 명시적으로 관리

### 1.3 이 프로젝트의 현재 상태

- `MockSendApiClient`가 `SimpleClientHttpRequestFactory` 사용 (지적 사항 그대로)
- `http.maxConnections` 설정 없음 → JDK 기본 5개 상한
- Worker concurrency 6 (실험-02 이후) → 이미 5개 상한을 살짝 초과

지적을 반영해 Apache HttpClient 5로 전환하는 것이 자연스러운 다음 개선.

### 1.4 SimpleClientHttpRequestFactory와 Apache HttpClient 5의 차이

두 방식은 "설정 가능한 것"의 층위 자체가 다르다. `SimpleClientHttpRequestFactory`도 아무 것도 못 하는 건 아니지만, HTTP 통신 튜닝의 대부분이 JVM 전역 프로퍼티에 얹혀 있는 구조라 세밀한 운영이 어렵다.

#### 커넥션 풀

| 항목 | SimpleClientHttpRequestFactory (JDK) | HttpComponentsClientHttpRequestFactory (HC5) |
|---|---|---|
| Pool 구현 | JDK 내부 `KeepAliveCache` | `PoolingHttpClientConnectionManager` (명시적 객체) |
| 총 커넥션 상한 | `-Dhttp.maxConnections` (JVM 전역, 기본 5) | `setMaxConnTotal(N)` (client 별) |
| Host별 상한 | JVM 전역만, host 구분 없음 | `setMaxConnPerRoute(N)` (route 별) |
| 여러 client 격리 | 불가 (JVM 하나면 pool 하나) | 가능 (client마다 별도 pool) |
| Pool 상태 관측 | 불가 (JDK 내부 캐시라 노출 없음) | `PoolStats` (leased/available/pending) 조회 가능 |

sendmock 하나만 호출하는 케이스는 그럭저럭 넘어가지만, 여러 외부 시스템(이메일 API + SMS API + 카카오 API 등)이 있으면 JDK 방식은 각각 격리된 pool을 만들 수 없다. 한 시스템이 느리면 다른 pool까지 연쇄 영향을 받는다. HC5는 client마다 별도 pool로 격리 가능.

#### Timeout

| 종류 | SimpleClientHttpRequestFactory | HC5 |
|---|---|---|
| Connect timeout (TCP 연결 대기) | O (`setConnectTimeout`) | O (`ConnectionConfig.setConnectTimeout`) |
| Read / Response timeout | O (`setReadTimeout`) | O (`RequestConfig.setResponseTimeout`) |
| Connection request timeout (pool 대여 대기) | X | O (`RequestConfig.setConnectionRequestTimeout`) |
| Socket SO_TIMEOUT (TCP 소켓 레벨) | X (JDK 내부 처리) | O (`SocketConfig.setSoTimeout`) |
| Validate after inactivity (idle 유효성 검증 임계) | X | O (`ConnectionConfig.setValidateAfterInactivity`) |
| Time to live (커넥션 최대 재사용 시간) | X | O (`ConnectionConfig.setTimeToLive`) |

JDK 방식은 두 timeout만 노출. HC5는 각 단계별로 세밀하게 설정할 수 있어 어디서 어떤 유형의 지연이 발생하는지 명확히 대응 가능.

- Pool이 만원인 상황에서 무한 대기를 피하려면 `connectionRequestTimeout`을 짧게 잡아 즉시 실패로 fallback
- TCP는 붙었는데 서버가 응답 안 주는 경우는 `responseTimeout`으로 무한 대기 방지
- TCP 소켓 자체가 갑자기 끊기는 half-open 감지는 `SocketConfig.setSoKeepAlive(true)`로 OS 레벨 keepalive 활성화

#### 커넥션 재사용과 정리 정책

서버가 keepalive로 유지하던 커넥션은 언젠가 close된다 (Spring Boot Tomcat 기본 60초). 클라이언트가 이를 모르면 죽은 커넥션을 pool에서 꺼내 요청 → 첫 바이트 실패 → 재시도 오버헤드가 발생한다.

| 정책 | JDK | HC5 |
|---|---|---|
| Idle 커넥션 자동 정리 | 없음 (혹은 JVM 내부 최소 로직) | `evictIdleConnections(TimeValue)` |
| 만료 커넥션 자동 정리 | 없음 | `evictExpiredConnections()` |
| 커넥션 재사용 전 유효성 검증 | 없음 | `validateAfterInactivity(TimeValue)` |
| Keepalive 전략 커스텀 | 불가 | `setKeepAliveStrategy(...)` |

JDK는 "그냥 재사용 시도, 실패하면 새로 만들기" 수준의 정책. HC5는 "idle 오래된 것은 아예 폐기, 사용 전 검증" 같은 방어 로직을 켤 수 있다.

#### 관측성

| 관측 대상 | JDK | HC5 |
|---|---|---|
| Pool 사용률 | 불가 | `PoolStats` API |
| Route별 leased/available/pending | 불가 | 지원 |
| Micrometer 통합 | 제한적 | 별도 통합 라이브러리 있음 |
| Connection lifecycle 로그 | 없음 | slf4j 로그 (org.apache.hc.*) |

성능 문제 진단 시 JDK 방식은 블랙박스. HC5는 지금 이 client의 pool이 얼마 차 있는지, 이 host로 요청이 얼마 대기 중인지 실시간 확인 가능.

#### 확장성

| 항목 | JDK | HC5 |
|---|---|---|
| Interceptor 체인 | 없음 | Request / Response interceptor 체인 |
| Retry 전략 커스텀 | 없음 | `HttpRequestRetryStrategy` 구현 가능 |
| Redirect 정책 커스텀 | 제한적 | `RedirectStrategy` |
| 인증 전략 | 기본만 | 다양한 스킴 지원 |
| Circuit Breaker 통합 | 별도 wrapping 필요 | Resilience4j 등과 통합 자연스러움 |

#### 요약

- `SimpleClientHttpRequestFactory`는 간단한 케이스에 적합하지만 튜닝 여지가 제한적이고, 튜닝의 대부분이 JVM 전역 시스템 프로퍼티에 의존한다
- `HttpComponentsClientHttpRequestFactory`(HC5)는 pool, timeout, 정리 정책, 관측을 코드로 명시적으로 설정하고, 여러 client를 격리할 수 있다. 운영에서 지시-관측-반응이 필요한 시나리오에 적합하다

`SimpleClientHttpRequestFactory`가 여전히 쓰이는 이유는 의존성 없이 JDK 내장이고, 단일 host / 저부하 케이스에서는 코드가 짧기 때문이다. 그러나 이 프로젝트처럼 부하 테스트, 성능 측정, 재시도, 동시 발송이 있는 시나리오에는 부적합.

---

## 2. 적용한 변경

### 의존성

```kotlin
// build.gradle.kts
implementation("org.apache.httpcomponents.client5:httpclient5")
```

### MockSendApiClient 코드

```java
PoolingHttpClientConnectionManager connectionManager =
    PoolingHttpClientConnectionManagerBuilder.create()
        .setMaxConnTotal(poolMaxTotal)         // 200
        .setMaxConnPerRoute(poolMaxPerRoute)   // 100
        .setDefaultConnectionConfig(ConnectionConfig.custom()
            .setConnectTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
            .setSocketTimeout(Timeout.of(readTimeoutMs, TimeUnit.MILLISECONDS))
            .build())
        .build();

RequestConfig requestConfig = RequestConfig.custom()
    .setConnectionRequestTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
    .setResponseTimeout(Timeout.of(readTimeoutMs, TimeUnit.MILLISECONDS))
    .build();

CloseableHttpClient httpClient = HttpClients.custom()
    .setConnectionManager(connectionManager)
    .setDefaultRequestConfig(requestConfig)
    .build();

HttpComponentsClientHttpRequestFactory requestFactory =
    new HttpComponentsClientHttpRequestFactory(httpClient);
```

Timeout 값(1s / 3s)은 이전과 동일하게 유지. 다른 시스템 설정(partition, concurrency, management port 등)은 실험-02와 동일.

---

## 3. 부하 테스트 결과

### 실행 매트릭스

| 회차 | 시작 조건 | 결과 요약 |
|---|---|---|
| 1차 | HttpClient 5 적용, 앱만 재빌드 (postgres/kafka는 warm) | 실패율 8.54% |
| 2차 | HttpClient 5 적용, 컨테이너 down/up (cold) | 실패율 95.72% |

### k6 결과 (steady 구간)

| 지표 | 실험-02 (Baseline) | 03 1차 | 03 2차 |
|---|---|---|---|
| p95 응답시간 | 11.42 ms | 32.02 s | 60 s (timeout) |
| p99 응답시간 | 25.23 ms | 58.45 s | 60 s |
| 실패율 | 0.00% | 8.54% | **95.72%** |
| 총 처리 요청 | 30,000 (정확) | 16,930 (전체) | 2,663 (전체) |
| dropped iterations | 0 | 17,459 | 31,726 |
| 실제 TPS | 100 | 40.3 | 6.3 |

### 서버 관측 (2차 붕괴 상태)

- app-api Prometheus scraping 실패로 DOWN 유지 (부하 시작 후 지속)
- worker는 정상 UP이지만 처리할 대상 없음 (API가 요청 자체를 못 받음)
- Kafka lag = 0 (API 미처리로 producer 발행 자체가 없음)

### app-api 로그

```
HikariPool-1 - Connection is not available, request timed out after 30001ms
(total=20, active=20, idle=0, waiting=22)

Outbox 즉시 발행 실패. polling 재시도 예정.
reason=Could not open JPA EntityManager for transaction
```

app-api의 Hikari pool 완전 소진 (20/20 active, 22 스레드 대기). 30초 대기 후 timeout. 이것이 k6가 관측한 32~60초 응답의 정체.

Cascading 흐름:
1. 요청 하나가 등록 트랜잭션(커넥션 #1) + AFTER_COMMIT listener 트랜잭션(커넥션 #2)에서 각각 커넥션 사용
2. 무언가 이유로 커넥션 #2 사용 시간이 길어짐
3. Pool 20개가 모두 잡히는 순간 발생
4. 새 요청은 30초 대기 후 timeout
5. Timeout 사이 새 요청이 계속 쌓임 → cascade 지속

---

## 4. 원인 격리 실험

HttpClient 5 자체가 원인인지, 콜드 시작이나 다른 요인이 원인인지 확인하기 위한 격리 실험.

### 실험 설계

| 실험 | HttpClient 5 | 컨테이너 상태 | 예상 |
|---|---|---|---|
| 실험-02 | 미적용 | warm | 안정 (실제 안정) |
| 실험-03 2차 | 적용 | cold | 붕괴 (실제 붕괴) |
| 격리 A | 미적용 (롤백) | cold | HC5 원인이면 안정, 콜드 원인이면 붕괴 |

### 격리 실험 A 결과

`git revert`로 HttpClient 5 변경분 되돌린 후, 컨테이너 완전 down/up (cold), 6 파티션 topic 재생성, DB TRUNCATE, sendmock RANDOM 유지 상태에서 동일 부하 시나리오 실행.

| 지표 | 실험-02 (warm) | 실험-03 2차 (HC5, cold) | 격리 A (롤백, cold) |
|---|---|---|---|
| p95 응답시간 | 11.42 ms | 60 s | **11.97 ms** |
| p99 응답시간 | 25.23 ms | 60 s | **33.59 ms** |
| 실패율 | 0.00% | 95.72% | **0.00%** |
| 처리 요청 (steady) | 30,001 | ~1,894 | **30,001** |
| 실제 TPS | 100 | 6.3 | **100** |
| 최대 VU 사용 | 3 | 300 (max) | **8** |

콜드 상태에서 롤백만으로 실험-02의 안정성이 완전히 재현됨. **HttpClient 5 도입이 붕괴의 실질적 직접 원인 확정**. 콜드 시작 자체는 우리 시스템 안정성에 유의미한 영향 없음.

---

## 5. 롤백 결정과 원인 규명 계획

### 즉시 결정

- `git revert f75ce5d`로 HttpClient 5 변경분 되돌림 유지
- HC5의 방향성 자체는 맞음 (코드리뷰 지적, 수업 내용 정확) — 우리 구현/설정에 문제가 있는 것으로 판단
- 원인 규명 실험(-05 예정)에서 HC5 옵션을 정상화한 뒤 재도전

### 원인 후보

**A. Connection 관리 옵션 누락**
- `evictIdleConnections`, `evictExpiredConnections` 설정 없음 → stale connection이 pool에 축적
- `validateAfterInactivity` 미설정 → pool에서 꺼낸 커넥션 유효성 검증 안 함
- 사용 후 stale connection으로 요청 실패 시 재시도 오버헤드가 워커 스레드 점유 시간을 늘림

**B. Timeout 설정 중복**
- `ConnectionConfig.setSocketTimeout` (소켓 레벨)과 `RequestConfig.setResponseTimeout` (요청 레벨)을 모두 설정
- HC5 내부에서 어느 것이 우선하는지, 어떻게 상호작용하는지 검증 필요

**C. Bean 초기화 부작용**
- `MockSendApiClient`는 `@Component`라 app-api와 app-worker 모두 인스턴스화
- app-api는 mock을 호출하지 않지만 bean 생성 자체가 pool과 관련 자원을 초기화
- 이 부수 자원이 app-api에 예상 밖 영향을 줄 가능성

**D. sendmock 서버측 close 정책**
- sendmock이 idle keepalive connection을 특정 시간 후 close
- HC5 pool에 stale connection 축적 → 재사용 시 실패
- 이 실패 처리가 워커 스레드/시간을 소모

**E. Spring 6와 HC5 통합의 특이점**
- Spring 6의 `HttpComponentsClientHttpRequestFactory`가 HC5의 특정 옵션과 예상과 다르게 상호작용할 가능성
- 공식 문서 최신 예시와 우리 사용 방식 대조 필요

### 규명 실험 계획

1. HC5 공식 문서 및 Spring 6 통합 예시 재확인 — best practice와 우리 코드 비교
2. `evictIdleConnections` / `evictExpiredConnections` 추가 후 재실험
3. `socketTimeout` 중복 제거 (RequestConfig만 사용) 후 재실험
4. Bean scope 격리 — worker에서만 bean 생성되도록 조건부 등록 (`@ConditionalOnProperty`)
5. sendmock keepalive 로그 확인 — 어느 시점에 커넥션이 close되는지
6. HC5 pool 상태 관측 — `PoolStats` 로그로 실제 사용률/유휴 커넥션 관찰

---

## 6. 부수 발견

- **Outbox listener sync 실행 취약점이 여전히 존재**. 실험-03의 붕괴 경로가 이를 노출. 어떤 원인으로 커넥션 #2 사용 시간이 길어지면 pool 소진이 재발할 수 있음. D(async listener) 개선의 시급성 재확인.
- **관측 격리(management port 9091 분리) 효과 검증**. app-api가 완전히 죽었음에도 app-worker의 지표 스크레이핑은 정상 유지되었음. 실험-01의 cascading failure처럼 관측 계층까지 무너지지는 않음.

---

## 7. 다음 실험

**실험-04 (원인 규명 우선, 옵션 1)**: HC5 옵션 완비 후 재실험
- 위 5. 규명 실험 계획의 항목 반영
- Connection eviction, timeout 정리, PoolStats 관측 등 개선
- 격리 실험으로 각 옵션 효과 검증
- 안정화 확인되면 HC5 유지, 여전히 문제면 후보 C(Bean 초기화 영향) 조사

**실험-05 (D 적용)**: sync outbox listener 근본 병목 제거
- `@Async` + Executor Bean 정의
- HC5 결과와 무관하게 진행 필요한 개선

---

## 8. 참고 파일

- k6 결과 (raw): `perf/k6/results/load-random-httpclient5-20260805-001132.json` (1차)
- k6 결과 (raw): `perf/k6/results/load-random-httpclient5-rerun-20260805-002416.json` (2차)
- k6 결과 (raw): `perf/k6/results/load-random-rollback-cold-<TS>.json` (격리 A)
- 각각 `-summary.json` / `-stdout.log` 동일 폴더
- Revert 커밋: `git log --oneline --grep=Revert`
