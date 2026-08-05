// 요청 payload 생성 유틸.
// - requestId: 매 iteration마다 UUID -> 서버의 중복 요청 dedup 로직 우회, 매번 신규 알림 생성
// - channel: EMAIL/SMS/KAKAO 라운드로빈
// - receiver: 채널별 합법 포맷 랜덤
// - message: iteration 인덱스가 붙은 고정 문자열

import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const CHANNELS = ['EMAIL', 'SMS', 'KAKAO'];

function randomReceiver(channel) {
    if (channel === 'EMAIL') {
        return `perf-${Math.floor(Math.random() * 100000)}@example.com`;
    }
    // SMS, KAKAO: 010 + 8자리 숫자 랜덤
    const n = Math.floor(Math.random() * 100000000).toString().padStart(8, '0');
    return `010${n}`;
}

/**
 * @param {number} iter - 현재 iteration 인덱스 (통상 __ITER)
 */
export function buildPayload(iter) {
    const channel = CHANNELS[iter % CHANNELS.length];
    return {
        requestId: uuidv4(),
        channel,
        receiver: randomReceiver(channel),
        title: 'perf-test',
        content: `perf test message #${iter}`,
    };
}
