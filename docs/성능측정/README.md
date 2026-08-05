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
| (이후) 실험-04-... | D (async listener) 등 근본 개선 후 재측정 |

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

- **실험-05**: D (Outbox `@Async` listener) 적용 후 재측정 — app-api Hikari 커넥션 #2 점유 자체 제거
- 실험-06 후보: 실험-05 후 격리 없이 HC5를 app-api에도 두는 버전으로 재실험 (근본 병목 제거되면 HC5 배치 유무 무관해질 것으로 예상)
- 후속 학습 과제: HC5 mechanism JFR 프로파일링 / 소스 분석 / bean-by-bean 세밀 격리
- 실험-07 후보: non-blocking mock client (Reactive 전환)
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
