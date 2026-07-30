-- 성능 측정 사이 데이터 초기화.
-- 앱이 관리하는 테이블만 truncate. RESTART IDENTITY로 auto-increment도 리셋.
-- CASCADE는 향후 FK 추가 시 안전 장치.
--
-- Postgres 자체 stat까지 리셋하고 싶으면 (시나리오 간 완전 격리):
--   SELECT pg_stat_statements_reset();
--   SELECT pg_stat_reset();

TRUNCATE notification RESTART IDENTITY CASCADE;
TRUNCATE outbox_event RESTART IDENTITY CASCADE;
