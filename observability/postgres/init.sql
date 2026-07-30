-- postgres_exporter가 slow query top-N을 수집할 수 있도록 확장 활성화.
-- shared_preload_libraries는 docker-compose에서 command로 지정한다.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
