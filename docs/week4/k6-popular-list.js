// 인기순 목록의 부하 측정 — 캐시가 동시 요청에서도 버티는지 본다.
//
// 실행 (VU 수를 바꿔가며 여러 번):
//   VUS=1  k6 run docs/week4/k6-popular-list.js
//   VUS=10 k6 run docs/week4/k6-popular-list.js
//   VUS=40 k6 run docs/week4/k6-popular-list.js
//
// 보는 것
//   http_req_duration p95/p99  — 꼬리가 어디서 터지나
//   http_reqs rate             — 처리량이 더 안 오르는 지점이 포화점이다
//
// 캐시를 비우고 재면 1회차만 미스고 나머지는 히트라 평균이 섞인다.
// setup 에서 한 번 불러 캐시를 채운 뒤 측정에 들어간다.

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8090';
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '20s';
const PATH = '/api/v1/products?sort=likes_desc&page=0&size=20';

export const options = {
  scenarios: {
    popular: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.get(`${BASE}${PATH}`);
  if (res.status !== 200) {
    throw new Error(`캐시 예열 실패 [${res.status}] ${res.body}`);
  }
  const data = JSON.parse(res.body).data;
  console.log(`예열 완료 — items ${data.items.length}, totalCount ${data.totalCount}`);
}

export default function () {
  const res = http.get(`${BASE}${PATH}`);

  check(res, {
    '200': (r) => r.status === 200,
    'items 20개': (r) => {
      if (r.status !== 200) return false;
      return JSON.parse(r.body).data.items.length === 20;
    },
  });
}
