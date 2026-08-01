# 성능 측정

알림 발송 시스템의 성능 특성을 관측 스택 위에서 측정하고, 발견된 병목을 개선한 뒤 재측정하는 실험 기록.

## 목표

수치 하나가 아니라 **병목 위치를 찾고 해석하고 개선하는 것**이 목적:
1. 병목 위치 파악 — API / Kafka / Consumer / Mock API / DB / Redis / JVM 중 어디가 먼저 무너지는가
2. TPS 한계 관찰 — 안정 구간 처리량과 붕괴 지점
3. 운영 관점 지표 대시보드 정립 — 실제 서비스에서 무엇을 봐야 하는지
4. **관측 → 병목 발견 → 개선 → 재측정**의 실험 사이클 실행

## 문서 구성

| 파일 | 내용 |
|---|---|
| [방법론.md](방법론.md) | 측정 환경, 자원 배분, 관측 개념, 시나리오 정의, 실행 절차, 트러블슈팅 (변하지 않는 배경) |
| [실험-01-baseline.md](실험-01-baseline.md) | Iteration 1: 기본 설정(partition=1, concurrency=1)에서의 측정. 붕괴 발견 + 병목 분석 |
| 실험-02-튜닝v1.md | Iteration 2: partition/concurrency 개선 후 재측정. 개선 효과 정량화 |
| (이후) 실험-03-... | 추가 개선 반복 |

각 실험 문서는 **self-contained**: 그 시점 시스템 상태 + 결과 + 병목 + 다음 개선안을 하나 안에.

## 실험 요약 표

| # | 상태 | 주요 발견 | Load(AS) p95 | Load(RANDOM) p95 | 실패율 | 링크 |
|---|---|---|---|---|---|---|
| 01 | partition=1, concurrency=1 | RANDOM에서 완전 붕괴, cascading failure로 관측까지 마비 | 11.7ms | **59.99s** (timeout) | 95.61% | [실험-01](실험-01-baseline.md) |
| 02 | partition=6, concurrency=6 | (진행 예정) | | | | (예정) |

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

- Load RANDOM 개선 후 재측정 (실험-02)
- Stress (baseline / tuned)
- Spike (baseline / tuned)
- 멀티 워커 스케일 실험 (`--scale app-worker=3`)
- Soak (장기 누적)

## 스코프 밖

- Alerting (Prometheus alertmanager, Grafana alerts)
- Distributed tracing (OpenTelemetry, Tempo)
- Log aggregation (Loki, ELK)
- CI 자동 성능 회귀 감지
- Circuit Breaker 라이브러리 도입
- 채널별 세밀한 rate limiter
