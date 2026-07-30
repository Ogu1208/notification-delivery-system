// spike 시나리오.
// 목적: 평상시 부하 중 갑자기 튀는 상황에서의 회복력 관찰.
// 구조: 워밍업 -> baseline -> 8x 스파이크 -> 복귀 -> 회복 관찰.
//
// 관찰 포인트:
//   - 스파이크 순간의 실패율/p99
//   - 스파이크 종료 후 Kafka lag가 얼마 만에 0으로 수렴하는가
//   - Hikari pending이 얼마 만에 정상화되는가
//   - RETRY_WAIT -> SENT 회복 흐름이 정상인가
//
// 실서비스에서 흔한 시나리오이지만 stress와 달리 "회복"에 초점.

import http from 'k6/http';
import { BASE_URL, summaryTrendStats } from '../lib/config.js';
import { buildPayload } from '../lib/payload.js';

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 1500,
            stages: [
                { target: 100, duration: '1m' },   // 워밍업
                { target: 100, duration: '2m' },   // baseline (100 TPS)
                { target: 800, duration: '10s' },  // 스파이크 (8배)
                { target: 800, duration: '30s' },  // 스파이크 유지
                { target: 100, duration: '20s' },  // 복귀
                { target: 100, duration: '2m' },   // 회복 관찰
                { target: 0, duration: '20s' },    // 램프다운
            ],
        },
    },
    thresholds: {
        // 스파이크 중 실패는 감안, 회복 후 안정성이 중요.
        http_req_failed: ['rate<0.15'],
    },
    summaryTrendStats,
};

export default function () {
    const payload = buildPayload(__ITER);
    http.post(`${BASE_URL}/notifications`, JSON.stringify(payload), {
        headers: { 'Content-Type': 'application/json' },
    });
}
