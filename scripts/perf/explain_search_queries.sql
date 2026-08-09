-- 조회 API 각 시나리오의 실행 계획 확인.
--
-- 전제: seed_notifications.sql로 100만 row 시딩 + ANALYZE 완료 상태.
--
-- 실행:
--   psql -h localhost -U notification -d notification -f scripts/perf/explain_search_queries.sql
--
-- 각 EXPLAIN (ANALYZE, BUFFERS) 결과에서 확인할 것:
--   - 어떤 인덱스가 태워지는가 (Index Scan / Bitmap Index Scan / Seq Scan)
--   - 예상 row 수 vs 실제 row 수 (통계가 정확한가)
--   - 실행 시간 (Total runtime)
--   - Buffer hit / read (캐시 효율)
--   - Sort가 인덱스로 회피되는가 (order by가 index scan 순서와 일치)

\echo '========================================='
\echo 'A. 요청자 기준 첫 페이지 (커서 없음, 최근 7일)'
\echo '========================================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notification
WHERE requester_id = 'svc-5'
  AND created_at >= NOW() - INTERVAL '7 days'
  AND created_at <  NOW()
ORDER BY created_at DESC, id DESC
LIMIT 21;

\echo ''
\echo '========================================='
\echo 'B. 요청자 기준 커서 있는 깊은 페이지 (500번째 페이지 근처)'
\echo '========================================='
-- 실제 서비스 상황: 클라이언트가 nextCursor 반복 요청해서 깊은 페이지에 도달.
-- 커서 조건은 튜플 비교 (created_at, id) < (X, Y)로 표현.
-- 인덱스 `(requester_id, created_at, id)`가 있으면 인덱스 seek로 완전 처리.
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notification
WHERE requester_id = 'svc-5'
  AND created_at >= NOW() - INTERVAL '7 days'
  AND created_at <  NOW()
  AND (created_at, id) < (NOW() - INTERVAL '3 days', 500000)
ORDER BY created_at DESC, id DESC
LIMIT 21;

\echo ''
\echo '========================================='
\echo 'C. 수신자 기준 첫 페이지'
\echo '========================================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notification
WHERE receiver = 'user-1234@example.com'
  AND created_at >= NOW() - INTERVAL '7 days'
  AND created_at <  NOW()
ORDER BY created_at DESC, id DESC
LIMIT 21;

\echo ''
\echo '========================================='
\echo 'D. 요청자 + 상태 필터 (SENT, FAILED)'
\echo '========================================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notification
WHERE requester_id = 'svc-5'
  AND created_at >= NOW() - INTERVAL '7 days'
  AND created_at <  NOW()
  AND status IN ('SENT', 'FAILED')
ORDER BY created_at DESC, id DESC
LIMIT 21;

\echo ''
\echo '========================================='
\echo 'E. 요청자 + 채널 필터 (EMAIL, SMS)'
\echo '========================================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notification
WHERE requester_id = 'svc-5'
  AND created_at >= NOW() - INTERVAL '7 days'
  AND created_at <  NOW()
  AND channel IN ('EMAIL', 'SMS')
ORDER BY created_at DESC, id DESC
LIMIT 21;

\echo ''
\echo '========================================='
\echo 'F. 요청자 + 수신자 조합 (특정 서비스가 특정 사용자에게)'
\echo '========================================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notification
WHERE requester_id = 'svc-5'
  AND receiver     = 'user-1234@example.com'
  AND created_at >= NOW() - INTERVAL '7 days'
  AND created_at <  NOW()
ORDER BY created_at DESC, id DESC
LIMIT 21;

\echo ''
\echo '========================================='
\echo 'G. 단건 상세 조회 (id로)'
\echo '========================================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notification
WHERE id = 500000;
