# 성능 측정

알림 발송 시스템의 성능 특성을 관측 스택 위에서 측정하고, 발견된 병목을 개선한 뒤 재측정하는 실험 기록.

## 목표

수치 하나가 아니라 **병목 위치를 찾고 해석하고 개선하는 것**이 목적:
1. 병목 위치 파악 — API / Kafka / Consumer / Mock API / DB / Redis / JVM 중 어디가 먼저 임계에 도달하는가
2. TPS 한계 관찰 — 안정 구간 처리량과 붕괴 지점
3. 운영 관점 지표 대시보드 정립 — 실제 서비스에서 무엇을 봐야 하는지
4. **관측 → 병목 발견 → 개선 → 재측정**의 실험 사이클 실행

## 문서 구성

| 파일 | 내용 |
|---|---|
| [방법론.md](방법론.md) | 측정 환경, 자원 배분, 관측 개념, 시나리오 정의, 실행 절차, 트러블슈팅 (변하지 않는 배경) |
| [실험-01-baseline.md](실험-01-baseline.md) | Iteration 1: 기본 설정(partition=1, concurrency=1)에서의 측정. 임계 초과 관측 + 병목 분석 |
| [실험-02-튜닝v1.md](실험-02-튜닝v1.md) | Iteration 2: A+B+E(partition/concurrency/관측 격리) 적용 후 재측정. API 안정화, worker 처리량 부분 개선 |
| [실험-03-httpclient5.md](실험-03-httpclient5.md) | Iteration 3: HttpClient 5 도입 시도 → 격리 A(롤백)/B(옵션 완비)/C(bean scope 격리)로 우회. mechanism은 미확정 |
| [실험-05-outbox-async.md](실험-05-outbox-async.md) | Iteration 5: Outbox listener `@Async` 분리 (D 적용). 근본 병목(sync + Hikari 소진) 제거. p95 40% 개선, cascade 원천 차단 |

각 실험 문서는 **self-contained**: 그 시점 시스템 상태 + 결과 + 병목 + 다음 개선안을 하나 안에.

## 실험 요약 표

| # | 상태 | 주요 발견 | Load(AS) p95 | Load(RANDOM) p95 | 실패율 | 링크 |
|---|---|---|---|---|---|---|
| 01 | partition=1, concurrency=1, actuator=8080 공유 | RANDOM에서 임계 초과, cascading failure로 관측 계층까지 영향 | 11.7ms | **59.99s** (timeout) | 95.61% | [실험-01](실험-01-baseline.md) |
| 02 | partition=6, concurrency=6, actuator=9091 분리 | API 완전 안정 (5,250배 개선). Worker 처리량 6배 향상됐으나 RANDOM 100 TPS 완전 처리엔 부족 (초당 8.6건) | (미측정) | **11.42ms** | 0.00% | [실험-02](실험-02-튜닝v1.md) |
| 03 v1 | 02 + HC5 (기본 옵션) | app-api 반복 붕괴. Hikari 완전 소진 + Outbox listener sync cascade | (미측정) | **60s (timeout)** | 95.72% | [실험-03](실험-03-httpclient5.md) |
| 격리 A | 03 v1 롤백 (SimpleClientHttpRequestFactory) + cold | 실험-02 재현 (안정). HC5 도입이 붕괴의 직접 원인 확정 | (미측정) | 11.97ms | 0.00% | (실험-03 § 3.1) |
| 격리 B | HC5 옵션 완비 (evict/validate/keepAlive) | 부분 개선 (실패율 95% → 10%). stale connection 이슈는 부분 원인이었지만 근본 원인 아님 | (미측정) | 57.79s | 10.10% | (실험-03 § 3.2) |
| 격리 C | HC5 옵션 완비 + bean scope 격리 (worker 전용) | **안정 확보** (실험-02와 동등). bean이 app-api에 존재하는 것 자체가 원인이었음. Mechanism 미확정 | (미측정) | **12.65ms** | 0.00% | (실험-03 § 3.3) |
| 05 | 격리 C + Outbox `@Async` 분리 (D 적용) | **근본 병목 제거**. Hikari active 대부분 0~1 (max 6), pending 계속 0 (이전 20/20 소진 + 22 대기). p95 40% 개선. cascade 원천 차단 | (미측정) | **7.58ms** | 0.00% | [실험-05](실험-05-outbox-async.md) |

## 스크린샷

`screenshots/` 하위에 실험별 대시보드 캡처 저장.
파일명 규칙: `<시나리오>-<mode>[-baseline|tuned]-<대시보드>.png`

예:
- `load-success-domain.png` — Load ALWAYS_SUCCESS 모드의 Domain 대시보드
- `load-random-baseline-app.png` — Load RANDOM 모드 baseline 상태의 Application 대시보드
- `load-random-tuned-domain.png` — Load RANDOM 모드 튜닝 후의 Domain 대시보드 (예정)

## 재현 방법

[방법론.md § 실행 절차](방법론.md#실행-절차) 참고.

## 아직 진행하지 않은 것

- **실험-06**: 격리 C 해제 후 재부하 (실험-05로 근본 병목 사라졌으니 HC5 bean이 app-api에 있어도 안정할지 검증. 실험-03의 mechanism 미스터리 자연 해소 여부 확인)
- **p99 tail latency 개선** (재현 확인 후): Kafka producer async batch → GC 튜닝(G1 → ZGC) 순
- 후속 학습 과제: HC5 mechanism JFR 프로파일링 / 소스 분석 / bean-by-bean 세밀 격리
- 후보: non-blocking 전면 전환 (WebFlux + R2DBC + Reactor Kafka) — 오버킬 가능성 크므로 학습 목적 외에는 스코프 밖
- Stress (tuned 상태에서 붕괴 지점 확인, management 포트 격리 효과 검증)
- Spike (회복력)
- Circuit Breaker (Resilience4j) 도입 실험
- 멀티 워커 스케일 실험 (`--scale app-worker=3`)
- Soak (장기 누적)

## 스코프 밖

- Alerting (Prometheus alertmanager, Grafana alerts)
- Distributed tracing (OpenTelemetry, Tempo)
- Log aggregation (Loki, ELK)
- CI 자동 성능 회귀 감지
- Circuit Breaker 라이브러리 도입
- 채널별 세밀한 rate limiter
