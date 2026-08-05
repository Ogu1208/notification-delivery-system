// k6 시나리오 공통 설정.
// 환경변수 K6_BASE_URL로 API 진입점을 오버라이드 가능 (기본은 로컬 Traefik).

export const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';

// summary 통계에 포함할 항목. p99까지 남겨야 병목 스토리 해석에 유용.
export const summaryTrendStats = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];
