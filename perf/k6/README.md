# k6 성능 측정 스크립트

## 사전 조건

1. 성능 측정 스택이 부팅되어 있어야 함:
   ```bash
   docker compose -f compose.yaml -f docker-compose.observability.yml up -d
   ./scripts/perf/verify-setup.sh
   ```

2. sendmock 프로세스가 실행 중이어야 함:
   ```bash
   cd /Users/ogu/study/과제전형\ 5기/알림발송시스템/sendmock
   ./gradlew bootRun
   # 원하는 모드로: STABLE | RANDOM
   ```

3. k6 설치:
   ```bash
   brew install k6
   ```

## 실행

```bash
mkdir -p perf/k6/results
TS=$(date +%Y%m%d-%H%M%S)
k6 run \
  --out json=perf/k6/results/load-stable-$TS.json \
  perf/k6/scenarios/load.js
```

## 시나리오

- `smoke.js`: 스크립트/앱 정상성 확인 (30초)
- `load.js`: 안정 구간 지표 (총 6분 30초)
- `stress.js`: 붕괴 지점 찾기 (총 약 12분)
- `spike.js`: 회복력 관찰 (총 약 6분)

## 데이터 초기화

각 시나리오 실행 전:
```bash
psql -h localhost -U notification -d notification -f scripts/perf/reset.sql
```

## 실행 매트릭스

| # | 시나리오 | Mock 모드 | 인스턴스 | 목적 |
|---|---|---|---|---|
| 1 | smoke | STABLE | api=1, worker=1 | 파이프라인 정상성 |
| 2 | load | STABLE | api=1, worker=1 | 순수 파이프라인 상한 |
| 3 | load | RANDOM | api=1, worker=1 | 재시도 부담 포함 처리량 |
| 4 | stress | STABLE | api=1, worker=1 | 붕괴 지점 |
| 5 | stress | RANDOM | api=1, worker=1 | 재시도 부하 겹침 시 붕괴 |
| 6 | spike | RANDOM | api=1, worker=1 | 회복력 |

STABLE/RANDOM 대비는 최소 (2 vs 3), (4 vs 5) 쌍은 남겨야 재시도 부담을 정량화할 수 있다.
