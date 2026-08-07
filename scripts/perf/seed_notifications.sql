-- notification 테이블에 100만 row 시딩. 조회 성능(EXPLAIN, 부하 테스트) 실험용.
--
-- 전제:
--   1. 스키마는 앱을 먼저 한 번 띄워 Hibernate가 만든 것을 사용한다 (컬럼명 정합성).
--   2. Postgres 컬럼명은 Spring Boot 기본 physical naming(snake_case) 기준.
--   3. request_id에 UNIQUE 제약이 걸려 있으므로 'req-seed-<n>' 형태로 유일성을 보장한다.
--
-- 데이터 분포 (deterministic — 재실행 시 동일 결과, EXPLAIN 재현성 확보):
--   - requester_id: svc-1 ~ svc-20 (각 5만 건)
--   - receiver:     user-1 ~ user-100000 (각 약 10건)
--   - channel:      EMAIL / SMS / KAKAO 순환 (각 약 33만 건)
--   - status:       SENT 우세(85%) + FAILED 5% + RETRY_WAIT 5% + DEAD 5%
--   - created_at:   최근 30일 랜덤 (7일 필터가 의미 있도록 앞뒤로 데이터 존재)
--
-- 실행:
--   psql -h localhost -U notification -d notification -f scripts/perf/seed_notifications.sql
--
-- 재실행:
--   기존 seed 데이터만 지우려면 DELETE FROM notification WHERE request_id LIKE 'req-seed-%';

INSERT INTO notification (
    request_id,
    requester_id,
    receiver,
    channel,
    title,
    content,
    status,
    attempt_count,
    retry_count,
    created_at,
    updated_at
)
SELECT
    'req-seed-' || gs,
    'svc-' || (1 + (gs % 20)),
    'user-' || (1 + (gs % 100000)) || '@example.com',
    (ARRAY['EMAIL', 'SMS', 'KAKAO'])[1 + (gs % 3)],
    'seed title ' || gs,
    'seed content ' || gs,
    CASE
        WHEN gs % 20 = 0 THEN 'FAILED'
        WHEN gs % 20 = 1 THEN 'RETRY_WAIT'
        WHEN gs % 20 = 2 THEN 'DEAD'
        ELSE 'SENT'
    END,
    1,
    0,
    NOW() - (random() * INTERVAL '30 days'),
    NOW() - (random() * INTERVAL '30 days')
FROM generate_series(1, 1000000) AS gs;

-- optimizer가 실제 분포를 반영한 실행 계획을 세우도록 통계 재수집.
ANALYZE notification;
