#!/bin/bash
# ------------------------------------------------------------------------------
# 성능 측정 인프라 정상성 검증.
# 매 세션 시작 전 실행해서 관측 스택과 앱이 정상 부팅됐는지 확인한다.
# 실패 지점마다 어디를 봐야 하는지 stdout으로 안내한다.
# ------------------------------------------------------------------------------

set -euo pipefail

# 프로젝트 루트로 이동 (스크립트 위치 기준: scripts/perf/ -> ../../).
# 어디서 실행하든(IntelliJ, 터미널, cron 등) compose.yaml 상대경로가 맞도록 보장.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."

COMPOSE_ARGS="-f compose.yaml -f docker-compose.observability.yml"

echo "==> [1/7] Docker Compose 서비스 상태"
docker compose $COMPOSE_ARGS ps

echo
echo "==> [2/7] Postgres 접속"
docker compose exec -T postgres psql -U notification -d notification -c "SELECT 1" \
  || (echo "FAIL: Postgres 접속 실패. 컨테이너 로그: docker compose logs postgres"; exit 1)

echo
echo "==> [3/7] Kafka topic 목록"
docker compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list \
  || (echo "FAIL: Kafka 접속 실패. 컨테이너 로그: docker compose logs kafka"; exit 1)

echo
echo "==> [4/7] Redis PING"
docker compose exec -T redis redis-cli PING \
  || (echo "FAIL: Redis 접속 실패. 컨테이너 로그: docker compose logs redis"; exit 1)

echo
echo "==> [5/7] 앱 헬스체크 (management 포트 9091 직접)"
# Actuator가 management.server.port=9091로 분리되어 있으므로 9091로 접근.
# 비즈니스 API는 Traefik(8080) 경유, actuator는 별도 포트로 격리.
curl -sf http://localhost:9091/actuator/health | grep -q '"status":"UP"' \
  || (echo "FAIL: 앱 헬스체크 실패. Traefik 대시보드: http://localhost:8082"; exit 1)
echo "OK"

echo
echo "==> [6/7] Prometheus targets 전부 UP인지 확인"
DOWN_COUNT=$(curl -sf http://localhost:9090/api/v1/targets \
  | python3 -c "import sys, json; d=json.load(sys.stdin); print(sum(1 for t in d['data']['activeTargets'] if t['health'] != 'up'))")
if [ "$DOWN_COUNT" -ne 0 ]; then
  echo "FAIL: $DOWN_COUNT개 target DOWN. Prometheus UI: http://localhost:9090/targets"
  exit 1
fi
echo "OK (전부 UP)"

echo
echo "==> [7/7] Grafana 접속"
# Grafana /api/health 응답: {"database": "ok", ...} (콜론 뒤 공백 있음)
curl -sf http://localhost:3000/api/health | grep -qE '"database":\s*"ok"' \
  || (echo "FAIL: Grafana 접속 실패. 컨테이너 로그: docker compose $COMPOSE_ARGS logs grafana"; exit 1)
echo "OK"

echo
echo "===> 성능 측정 환경 준비 완료"
echo "   Grafana:    http://localhost:3000"
echo "   Prometheus: http://localhost:9090"
echo "   Traefik:    http://localhost:8082"
