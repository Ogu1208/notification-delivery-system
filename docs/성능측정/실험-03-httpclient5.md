# 실험 03 — Apache HttpClient 5 도입 시도 (실패, 롤백)

코드리뷰와 수업 내용을 반영해 `SimpleClientHttpRequestFactory`(JDK `HttpURLConnection` 기반) 대신 Apache HttpClient 5 + `HttpComponentsClientHttpRequestFactory`로 전환을 시도했으나, RANDOM 부하 하에서 API 계층이 반복적으로 붕괴됨. 격리 실험으로 HttpClient 5 도입이 붕괴의 직접 원인임을 확인하고, 원인 규명 전 롤백 결정.

배경 개념과 실행 절차는 [방법론.md](방법론.md) 참고. 관련 실험은 [실험-02-튜닝v1.md](실험-02-튜닝v1.md) 참고.

---

## 1. 시도한 개선 — 배경

### 1.1 코드리뷰 지적 사항

```
4. 외부 API 커넥션 풀이 없습니다
SimpleClientHttpRequestFactory는 JDK 기본 HTTP 연결을 쓰기 때문에
커넥션 풀 크기를 지정할 수 없고, 대상 호스트당 5개로 묶입니다.

동시 발송이 그보다 많아지면 매 호출마다 연결을 새로 맺습니다.
build.gradle.kts에 HTTP 클라이언트 의존성이 없어 기본 구현으로 잡히신 것 같은데,
Apache HttpClient 5를 추가하고 풀 크기를 명시하시는 걸 권장드립니다.
```

### 1.2 수업 내용 요지

- `SimpleClientHttpRequestFactory`는 JDK `HttpURLConnection`을 사용
- JDK 내부 `KeepAliveCache`의 host별 재사용 커넥션 상한이 기본 5개 (`http.maxConnections` 시스템 프로퍼티)
- 동시 요청이 5개 초과면 매번 새 TCP 연결 필요 (오버헤드)
- 앞단에서 병렬 요청 수를 늘리면 이 상한이 병목이 됨
- 해결: `HttpComponentsClientHttpRequestFactory`로 Apache HttpClient 5 붙여 pool 크기 명시 관리

### 1.3 우리 상태 확인

- MockSendApiClient에 `SimpleClientHttpRequestFactory` 사용 중 (지적 사항 그대로)
- `http.maxConnections` 설정 없음 → JDK 기본 5개 상한
- Worker concurrency 6 (실험-02 이후) → 이미 5개 상한을 살짝 초과

지적을 반영해 Apache HttpClient 5로 전환하는 것이 자연스러운 다음 개선.

### 1.4 SimpleClientHttpRequestFactory vs Apache HttpClient 5 상세 비교

두 방식이 사실 "설정 가능한 것"의 층위 자체가 다름. `SimpleClientHttpRequestFactory`도 아무 것도 못 하는 건 아니지만, **HTTP 통신 튜닝의 대부분이 JVM 전역 프로퍼티에 얹혀 있는 구조**라 세밀한 운영이 어려움.

#### (1) 커넥션 풀 (핵심 차이)

| 구분 | SimpleClientHttpRequestFactory (JDK) | HttpComponentsClientHttpRequestFactory (HC5) |
|---|---|---|
| Pool 구현 | JDK 내부 `KeepAliveCache` | `PoolingHttpClientConnectionManager` (명시적 객체) |
| 총 커넥션 상한 | `-Dhttp.maxConnections` (JVM 전역, 기본 5) | `setMaxConnTotal(N)` (client 별) |
| Host별 상한 | JVM 전역 프로퍼티만, host 구분 없음 | `setMaxConnPerRoute(N)` (route 별) |
| 여러 client 격리 | 불가 (JVM 하나면 pool 하나) | 가능 (client마다 별도 pool) |
| Pool 상태 관측 | 불가 (JDK 내부 캐시라 노출 없음) | `PoolStats` (leased/available/pending) 조회 가능 |

**의미**: 우리처럼 sendmock 하나만 호출하는 케이스는 그럭저럭 넘어가지만, **여러 외부 시스템(예: 이메일 API + SMS API + 카카오 API)이 있으면 JDK 방식은 각각 격리된 pool을 못 만듦**. 하나 시스템이 느리면 다른 pool까지 연쇄 영향. HC5는 client마다 별도 pool로 격리.

#### (2) Timeout 종류

| 종류 | SimpleClientHttpRequestFactory | HC5 |
|---|---|---|
| Connect timeout (TCP 연결 대기) | O (`setConnectTimeout`) | O (`ConnectionConfig.setConnectTimeout`) |
| Read timeout / Response timeout | O (`setReadTimeout`) | O (`RequestConfig.setResponseTimeout`) |
| **Connection request timeout** (pool에서 커넥션 대여 대기) | X | O (`RequestConfig.setConnectionRequestTimeout`) |
| **Socket SO_TIMEOUT** (TCP 소켓 레벨) | X (JDK 내부에서 처리) | O (`SocketConfig.setSoTimeout`) |
| **Validate after inactivity** (idle 커넥션 유효성 검증 임계) | X | O (`ConnectionConfig.setValidateAfterInactivity`) |
| **Time to live** (커넥션 최대 재사용 시간) | X | O (`ConnectionConfig.setTimeToLive`) |

**의미**: JDK 방식은 사실상 두 timeout만 노출됨. HC5는 각 단계별로 다른 timeout을 설정할 수 있어 **어디서 어떤 유형의 지연이 발생하는지 세밀하게 대응 가능**.

- 예: Pool이 만원인 상황에서 계속 무한 대기하면 안 되니 `connectionRequestTimeout` 을 짧게 (100ms). Pool에서 못 얻으면 즉시 실패로 fallback.
- 예: TCP는 붙었는데 서버가 응답 안 주면 `responseTimeout` (3s). 무한 대기 방지.
- 예: TCP 소켓 자체가 갑자기 끊기는 half-open 감지는 `SocketConfig.setSoKeepAlive(true)` 로 OS 레벨 keepalive 활성화.

#### (3) 커넥션 재사용/정리 정책

**서버가 keepalive로 유지하던 커넥션을 언젠가는 close 함** (Spring Boot Tomcat 기본 60초). 클라이언트가 이걸 모르면 "죽은 커넥션"을 pool에서 꺼내 요청 → 첫 바이트 실패 → 재시도. 이 오버헤드가 클 수 있음.

| 정책 | JDK | HC5 |
|---|---|---|
| Idle 커넥션 자동 정리 | 없음 (혹은 JVM 내부 최소 로직) | `evictIdleConnections(TimeValue)` |
| 만료 커넥션 자동 정리 | 없음 | `evictExpiredConnections()` |
| 커넥션 재사용 전 유효성 검증 | 없음 | `validateAfterInactivity(TimeValue)` |
| Keepalive 전략 커스텀 | 불가 | `setKeepAliveStrategy(...)` |

**의미**: JDK 방식은 "그냥 재사용 시도, 실패하면 새로 만들기" 정도의 정책. HC5는 **"idle 오래된 건 아예 폐기하고, 사용 전 검증"** 같은 방어 로직을 켤 수 있음.

#### (4) 관측성

| 관측 대상 | JDK | HC5 |
|---|---|---|
| Pool 사용률 | 불가 | `PoolStats` API |
| Route 별 leased/available/pending | 불가 | 지원 |
| Micrometer 통합 | 제한적 | 별도 통합 라이브러리 있음 |
| Connection lifecycle 로그 | slf4j 없음 | slf4j 로그 (org.apache.hc.*) |

**의미**: 성능 문제 진단 시 JDK 방식은 "블랙박스". HC5는 **"지금 이 client의 pool이 얼마 차 있는지, 이 host로 요청이 얼마 대기 중인지"** 실시간 확인 가능.

#### (5) 확장성

| 항목 | JDK | HC5 |
|---|---|---|
| Interceptor 체인 | 없음 | Request/Response interceptor 체인 |
| Retry 전략 커스텀 | 없음 | `HttpRequestRetryStrategy` 구현 가능 |
| Redirect 정책 커스텀 | 제한적 | `RedirectStrategy` |
| 인증 전략 | 기본만 | 다양한 스킴 지원 |
| Circuit Breaker 통합 | 별도 wrapping 필요 | Resilience4j 등과 통합 자연스러움 |

#### (6) 설정 가능성 한 줄 요약

- **JDK/SimpleClientHttpRequestFactory**: "간단한 케이스에 적합, 하지만 튜닝 여지가 제한적. 튜닝은 대부분 JVM 전역 시스템 프로퍼티에 의존"
- **HC5/HttpComponentsClientHttpRequestFactory**: "코드로 명시적으로 pool, timeout, 정리 정책, 관측을 다 설정. 여러 client 격리 가능. 운영에서 지시하고 관측하고 반응하기에 적합"

#### (7) 그럼에도 SimpleClientHttpRequestFactory가 여전히 쓰이는 이유

- 의존성 없음 (JDK 내장)
- 간단한 케이스(단일 host, 저부하)에 코드가 짧음
- 학습 곡선 낮음

**그러나 이 프로젝트처럼 부하 테스트/성능 측정/재시도/동시 발송이 있는 시나리오에서는 부적합**. 코드리뷰가 지적한 이유가 바로 이것.

---

## 2. 적용한 변경

### 2.1 의존성 추가
```kotlin
// build.gradle.kts
implementation("org.apache.httpcomponents.client5:httpclient5")
```

### 2.2 MockSendApiClient 코드 변경
```java
// SimpleClientHttpRequestFactory 제거, HttpComponentsClientHttpRequestFactory로 대체
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

### 2.3 관측성 유지
- Timeout 값(1s/3s)은 이전과 동일하게 유지
- Pool 크기는 우리 동시 발송(concurrency 6) 대비 충분히 큰 값 사용
- 다른 시스템 설정(partition, concurrency, management port 등)은 실험-02와 동일

---

## 3. 실행 결과 — 반복 붕괴

### 3.1 실행 매트릭스

| 회차 | 시작 조건 | 결과 |
|---|---|---|
| 1차 | HttpClient 5 적용, 앱만 재빌드 (postgres/kafka는 warm) | 부분 붕괴 (실패율 8.54%) |
| 2차 | HttpClient 5 적용, 컨테이너 down/up (cold) | 완전 붕괴 (실패율 95.72%) |

### 3.2 k6 결과 (steady 구간)

| 지표 | 실험-02 (Baseline) | 03 1차 | 03 2차 |
|---|---|---|---|
| p95 응답시간 | 11.42 ms | 32.02 s | 60 s (timeout) |
| p99 응답시간 | 25.23 ms | 58.45 s | 60 s |
| 실패율 | 0.00% | 8.54% | **95.72%** |
| 총 처리 요청 | 30,000 (정확) | 16,930 (전체) | 2,663 (전체) |
| dropped iterations | 0 | 17,459 | 31,726 |
| 실제 TPS | 100 | 40.3 | 6.3 |

### 3.3 서버 관측 (2차 붕괴 상태)

- **app-api 스크레이핑 실패 (DOWN 유지)**: Prometheus targets 조회 결과, app-api가 부하 시작 후 지속 DOWN
- **worker는 정상 UP**: worker 자체는 처리를 시도하나 app-api가 요청을 안 받아 처리할 게 없음
- **Kafka lag = 0**: API가 요청 자체를 처리 못 하니 producer 발행도 없음

### 3.4 app-api 로그 원인 실증

```
HikariPool-1 - Connection is not available, request timed out after 30001ms
(total=20, active=20, idle=0, waiting=22)

Outbox 즉시 발행 실패. polling 재시도 예정.
reason=Could not open JPA EntityManager for transaction
```

**app-api의 Hikari pool 완전 소진** (20/20 active, 22 스레드 대기). 30초 대기 후 timeout. 이것이 k6가 관측한 32~60초 응답의 정체.

Cascading 흐름:
1. 요청 하나가 등록 트랜잭션(커넥션 #1) + AFTER_COMMIT listener 트랜잭션(커넥션 #2)에서 각각 커넥션 사용
2. 무언가 이유로 커넥션 #2 사용 시간이 길어짐
3. Pool 20개가 모두 잡히는 순간 발생
4. 새 요청은 30초 대기 후 timeout
5. Timeout 사이 새 요청이 계속 쌓임 → cascade 지속

---

## 4. 원인 격리 실험

HttpClient 5 자체가 원인인지, 콜드 시작이나 다른 요인이 원인인지 확인하기 위해 격리 실험 수행.

### 4.1 실험 설계

| 실험 | HttpClient 5 | 컨테이너 상태 | 예상 결과 (가설별) |
|---|---|---|---|
| 실험-02 | 미적용 | warm | 안정 (실제로 안정) |
| 실험-03 2차 | **적용** | cold | 붕괴 (실제로 붕괴) |
| **격리 실험 A** | **미적용 (롤백)** | **cold** | HttpClient 5 원인이면 안정, 콜드 원인이면 붕괴 |

### 4.2 격리 실험 결과

`git revert`로 HttpClient 5 변경분 되돌린 후, 컨테이너 완전 down/up (콜드), 6 파티션 topic 재생성, DB TRUNCATE, sendmock RANDOM 유지 상태에서 동일 부하 시나리오 실행.

| 지표 | 실험-02 (warm) | 실험-03 2차 (HC5, cold) | **격리 A (롤백, cold)** |
|---|---|---|---|
| p95 응답시간 | 11.42 ms | 60 s | **11.97 ms** |
| p99 응답시간 | 25.23 ms | 60 s | **33.59 ms** |
| 실패율 | 0.00% | 95.72% | **0.00%** |
| 처리 요청 (steady) | 30,001 | ~1,894 | **30,001** |
| 실제 TPS | 100 | 6.3 | **100** |
| 최대 VU 사용 | 3 | 300 (max) | **8** |

### 4.3 결론

**HttpClient 5 도입이 붕괴의 실질적 직접 원인 확정**. 콜드 시작 자체는 우리 시스템 안정성에 유의미한 영향 없음 (같은 콜드에서 롤백 상태는 완벽히 안정).

---

## 5. 결정 — 롤백 유지 (원인 규명 후 재도입 검토)

### 5.1 즉시 결정
- `git revert f75ce5d` 로 HttpClient 5 변경분 되돌림 유지
- 다음 실험(-04)은 롤백 상태에서 D(async listener) 적용

### 5.2 원인 규명 필요성 (미해결)

**"HttpClient 5를 도입하지 않는다"는 최종 결정이 아님**. 코드리뷰 지적 사항이자 수업에서 강조한 표준 대응이므로, **우리 설정/구현에 잘못이 있음을 전제**하고 원인을 파고들어야 함.

### 5.3 원인 후보 (조사 필요)

**A. Connection 관리 옵션 누락**
- `evictIdleConnections`, `evictExpiredConnections` 설정 없음 — stale connection이 pool에 축적될 가능성
- `validateAfterInactivity` 미설정 — pool에서 꺼낸 커넥션이 실제 유효한지 확인 안 함
- 사용 후 stale connection으로 요청 실패 시 재시도 오버헤드가 워커 스레드를 오래 잡을 수 있음

**B. Timeout 설정 중복/충돌**
- `ConnectionConfig.setSocketTimeout` (소켓 레벨)
- `RequestConfig.setResponseTimeout` (요청 레벨)
- 두 값 모두 설정. HC5 내부에서 어느 것이 우선하는지, 상호작용이 어떤지 검증 필요

**C. Bean 초기화 부작용**
- MockSendApiClient는 `@Component`라 app-api와 app-worker 두 컨테이너 모두 인스턴스화됨
- app-api는 mock을 호출하지 않지만 bean 생성 자체가 pool + evictor 스레드를 만듦
- 이 부수 자원이 app-api에 예상 밖 영향을 줄 가능성 (검증 필요)

**D. sendmock 서버측 close 정책**
- sendmock이 idle keepalive connection을 특정 시간 후 close
- HC5 pool에 stale connection 축적 → 재사용 시 실패
- 이 실패 처리가 워커 스레드/시간 소모

**E. Spring 6 + HC5 통합 상 미묘한 차이**
- Spring 6.x의 `HttpComponentsClientHttpRequestFactory`가 HC5의 특정 옵션과 상호작용 시 예상과 다르게 동작할 가능성
- 문서 최신 예시와 우리 사용 방식 비교 필요

### 5.4 다음 단계 (구체적 조사 항목)

1. **HC5 공식 문서 및 Spring 6 통합 예시 재확인** — best practice와 우리 코드 비교
2. **`evictIdleConnections` / `evictExpiredConnections` 추가** 후 재실험
3. **`socketTimeout` 중복 제거** (RequestConfig만 사용) 재실험
4. **Bean scope 격리** — worker에서만 bean 생성되도록 조건부 등록 (`@ConditionalOnProperty`)
5. **sendmock keepalive 로그** 확인 — 어떤 시점에 커넥션이 close되는지
6. **HC5 pool 상태 관측** — `PoolStats` 로그로 실제 사용률/유휴 커넥션 관찰

이 조사와 원인 규명은 **별도 실험(-05 or 그 이후)**으로 진행. 다음 실험(-04)은 지금 아는 근본 병목(sync outbox listener)을 D로 해결하는 것을 우선.

---

## 6. 이 실험이 남긴 것

### 6.1 실증된 것
- HttpClient 5 마이그레이션은 **단순 라이브러리 교체가 아니라 세밀한 옵션 튜닝이 필요한 작업**임을 실증
- 기본 옵션만으로는 오히려 성능/안정성 저하 가능
- 격리 실험(변수 하나씩 통제)이 원인 확정에 유효

### 6.2 얻은 부수 발견
- **Outbox listener sync 실행 취약점이 여전히 존재** (실험-03의 붕괴 경로가 이걸 노출) — D 개선의 시급성 재확인
- **관측 격리(management port 9091 분리) 효과 검증** — app-api가 완전히 죽었음에도 app-worker는 지표 스크레이핑 정상 유지. 실험-01의 cascading failure 시나리오처럼 관측까지 무너지지는 않았음

### 6.3 문서화된 실패의 가치
- "시도 → 실패 → 롤백 → 원인 격리 → 후속 조사 계획"의 실험 사이클 실증
- 코드리뷰 지적의 방향은 옳지만 구현 세부에서 문제 발생. **정답 방향도 잘못된 구현은 문제** 임을 기록

---

## 7. 다음 실험 계획

**실험-04**: D (Outbox listener `@Async` 분리) 적용
- 근본 병목(sync 실행에 따른 API 스레드 커넥션 #2 점유) 제거
- 예상: 어떤 HTTP 클라이언트 조합에서도 API layer 안정 유지

**실험-05 (후속, HC5 재도전)**: 위 5.4 조사 항목 진행 후 HttpClient 5 재적용
- Connection eviction, timeout 정리, PoolStats 관측 등 반영
- 격리 실험으로 각 옵션 효과 검증

---

## 8. 참고 파일

- k6 결과 (raw): `perf/k6/results/load-random-httpclient5-20260805-001132.json` (1차)
- k6 결과 (raw): `perf/k6/results/load-random-httpclient5-rerun-20260805-002416.json` (2차)
- k6 결과 (raw): `perf/k6/results/load-random-rollback-cold-<TS>.json` (격리 A)
- 각각 `-summary.json` / `-stdout.log` 동일 폴더
- Revert 커밋: `git log --oneline --grep=Revert` 로 조회
