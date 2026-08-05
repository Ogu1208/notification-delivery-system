// smoke 시나리오.
// 목적: 스크립트/앱/의존 서비스가 정상 살아있는지 매 세션 시작 전 항상 먼저 확인.
// VU 1 x 30초의 최소 부하로 202 응답과 낮은 실패율만 검증.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, summaryTrendStats } from '../lib/config.js';
import { buildPayload } from '../lib/payload.js';

export const options = {
    vus: 1,
    duration: '30s',
    thresholds: {
        // smoke는 관대하지 않다: 실패율 1% 이상이면 문제.
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
    summaryTrendStats,
};

export default function () {
    const payload = buildPayload(__ITER);
    const res = http.post(`${BASE_URL}/notifications`, JSON.stringify(payload), {
        headers: { 'Content-Type': 'application/json' },
        tags: { phase: 'smoke' },
    });

    check(res, {
        'status is 202': (r) => r.status === 202,
    });
}
