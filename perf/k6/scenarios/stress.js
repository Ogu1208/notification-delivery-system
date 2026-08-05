// stress 시나리오.
// 목적: 부하를 계단식으로 올려서 어느 지점에서 시스템이 무너지는지 관찰.
// 구조: 각 계단 = 30초 램프업 + 2분 hold(안정된 지표 캡처).
//
// 왜 각 계단에 hold를 두나:
//   - 램프 중에는 지표가 튀어서 "이 부하에서 안정된 성능이 얼마"인지 알기 어렵다.
//   - 2분 hold에서 안정된 p95/에러율/lag를 캡처해 계단별로 비교.
//
// 목표는 붕괴 지점 발견. 실패율/응답시간이 임계를 넘는 계단을 문서에 기록.
// threshold는 관대하게 잡음(실패율 10% 미만) — 이 시나리오는 실패를 관찰하러 감.

import http from 'k6/http';
import { BASE_URL, summaryTrendStats } from '../lib/config.js';
import { buildPayload } from '../lib/payload.js';

export const options = {
    scenarios: {
        stepped_ramp: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 2000,   // 붕괴 시점에 부족하지 않도록 여유.
            stages: [
                { target: 100, duration: '30s' },  // 계단 1: 100 TPS 램프
                { target: 100, duration: '2m' },   // 계단 1: hold (관찰)
                { target: 300, duration: '30s' },
                { target: 300, duration: '2m' },
                { target: 600, duration: '30s' },
                { target: 600, duration: '2m' },
                { target: 1000, duration: '30s' },
                { target: 1000, duration: '2m' },
                { target: 1500, duration: '30s' },
                { target: 1500, duration: '2m' },  // 여기서 붕괴 여부 관찰
                { target: 0, duration: '30s' },    // 램프다운
            ],
        },
    },
    thresholds: {
        // stress는 붕괴를 관찰하러 오는 것이므로 임계를 관대하게.
        http_req_failed: ['rate<0.10'],
    },
    summaryTrendStats,
};

export default function () {
    const payload = buildPayload(__ITER);
    http.post(`${BASE_URL}/notifications`, JSON.stringify(payload), {
        headers: { 'Content-Type': 'application/json' },
    });
}
