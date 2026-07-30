// load 시나리오.
// 목적: 목표 TPS를 유지할 때 안정 구간의 p95/p99/에러율/lag를 관측.
// 구조: 워밍업(1m) -> steady(5m) -> 램프다운(30s).
//
// 왜 ramping-arrival-rate인가:
//   - 0에서 갑자기 100 TPS로 뛰면 JIT/커넥션 풀/캐시 콜드 스타트로 초기 수치가 오염.
//   - 램프업으로 부드럽게 올려서 워밍업 구간을 명확히 분리하고,
//     steady 태그만 threshold와 문서화에 사용.
//
// phase 태그는 setup()에서 시작 시각을 반환받아 default(data)에서 경과 시간으로 판정.
// (k6는 setup 반환값을 default 함수 인자로 전달한다.)
//
// 초기 목표 rate 100 TPS는 감으로 잡은 값. smoke/load 반복하면서 조정 가능.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, summaryTrendStats } from '../lib/config.js';
import { buildPayload } from '../lib/payload.js';

// 워밍업이 끝나는 시각(초) — 이후 요청은 steady 태그로 분류.
const WARMUP_SECONDS = 60;

export const options = {
    scenarios: {
        warmup_and_load: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 300,
            stages: [
                { target: 100, duration: '1m' },   // 워밍업 (0 -> 100 TPS)
                { target: 100, duration: '5m' },   // steady (측정 대상)
                { target: 0, duration: '30s' },    // 램프다운
            ],
        },
    },
    thresholds: {
        // steady 구간만 임계값 적용 — 워밍업 오염 방지.
        'http_req_duration{phase:steady}': ['p(95)<200', 'p(99)<500'],
        'http_req_failed{phase:steady}': ['rate<0.01'],
    },
    summaryTrendStats,
};

// setup은 시나리오 시작 전 한 번 실행. 반환값이 default에 인자로 전달됨.
export function setup() {
    return { startAtMs: Date.now() };
}

export default function (data) {
    // 시나리오 시작 후 경과 초. WARMUP_SECONDS 전까지는 warmup 태그.
    const elapsedSec = (Date.now() - data.startAtMs) / 1000;
    const phase = elapsedSec < WARMUP_SECONDS ? 'warmup' : 'steady';

    const payload = buildPayload(__ITER);
    const res = http.post(`${BASE_URL}/notifications`, JSON.stringify(payload), {
        headers: { 'Content-Type': 'application/json' },
        tags: { phase },
    });

    check(res, {
        'status is 202': (r) => r.status === 202,
    });
}
