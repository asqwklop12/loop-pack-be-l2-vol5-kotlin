// 결제 부하를 단계로 올리면서 무엇이 먼저 망가지는지 본다.
//
//   VUS=10   k6 run docs/week5/k6-payment-ramp.js           동시 10 으로 30초
//   VUS=1000 ITERATIONS=100000 k6 run ...                    동시 1000 으로 10만 건
//
// 단계 전체는 docs/week5/run-payment-ramp.sh 가 돌린다.
//
// VU 를 10만까지 올리는 것은 이 머신에서 안 된다. k6 는 VU 당 약 290 KiB 를
// 쓰므로 10만 VU 는 약 29 GiB 다. 10만은 동시 수가 아니라 ITERATIONS 로 준다.
//
// 총량 모드에서는 MAX_DURATION 이 control 레인의 길이도 정한다. payment 가
// 먼저 끝나면 그 시간만큼 빈 구간이 섞이므로, 실제 소요에 가깝게 준다.
//
// 세 레인을 같이 쏘는 것이 핵심이다.
//   payment  주문 생성 → 확정(카드) → PG 호출.        부하를 올리는 쪽
//   heavy    상품 목록 조회. DB 를 무겁게 쓴다.         한가할 때도 ~580ms
//   light    없는 경로. 404 를 돌려주고 DB 를 안 탄다.  한가할 때 ~1ms
//
// 결제와 상관없는 두 레인이 같이 느려지면 결제가 다른 API 를 끌고 내려간 것이다.
// 둘로 나눈 이유는 원인이 갈리기 때문이다 — light 까지 느려지면 요청을 처리할
// 스레드가 없는 것이고, heavy 만 느려지면 DB 쪽에서 밀리는 것이다.
//
// 두 레인은 constant-arrival-rate 다. VU 를 공유하면 payment 가 VU 를 다 쥐고
// 있는 동안 요청을 못 보내서, 느려진 게 아니라 안 보낸 것이 된다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE = __ENV.BASE_URL || 'http://localhost:8099';
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '30s';
const CARD_NO = __ENV.CARD_NO || '1234-5678-9814-1451';
const CARD_TYPE = __ENV.CARD_TYPE || 'SAMSUNG';
const IDS_FILE = __ENV.IDS_FILE || 'product-ids.json';
const CONTROL_RATE = Number(__ENV.CONTROL_RATE || 5);
const ITERATIONS = Number(__ENV.ITERATIONS || 0);
// 총량 모드에서 control 레인의 duration 을 길게 잡으면, payment 가 10만 건을
// 다 끝낸 뒤에도 control 만 남아서 빈 시간을 돈다. 둘을 같은 값으로 묶는다.
const RUN_FOR = ITERATIONS ? __ENV.MAX_DURATION || '10m' : DURATION;
const OUT = __ENV.OUT || '';

const productIds = new SharedArray('productIds', () => JSON.parse(open(IDS_FILE)));

const createLatency = new Trend('step_create', true);
const confirmLatency = new Trend('step_confirm', true);
const heavyLatency = new Trend('step_heavy', true);
const lightLatency = new Trend('step_light', true);

const createRejected = new Counter('create_rejected');
const confirmOk = new Counter('confirm_ok');
const confirmRejected = new Counter('confirm_rejected');
const confirmBroken = new Counter('confirm_broken');

export const options = {
  discardResponseBodies: false,
  scenarios: {
    payment: ITERATIONS
      ? {
          executor: 'shared-iterations',
          vus: VUS,
          iterations: ITERATIONS,
          maxDuration: RUN_FOR,
          exec: 'payment',
          tags: { lane: 'payment' },
        }
      : {
          executor: 'constant-vus',
          vus: VUS,
          duration: DURATION,
          exec: 'payment',
          tags: { lane: 'payment' },
        },
    heavy: {
      executor: 'constant-arrival-rate',
      rate: CONTROL_RATE,
      timeUnit: '1s',
      duration: RUN_FOR,
      preAllocatedVUs: Math.max(10, CONTROL_RATE * 2),
      maxVUs: 400,
      exec: 'heavy',
      tags: { lane: 'heavy' },
    },
    light: {
      executor: 'constant-arrival-rate',
      rate: CONTROL_RATE,
      timeUnit: '1s',
      duration: RUN_FOR,
      preAllocatedVUs: Math.max(10, CONTROL_RATE),
      maxVUs: 400,
      exec: 'light',
      tags: { lane: 'light' },
    },
  },
  thresholds: {
    'step_light': ['p(99)<100'],
    'step_heavy': ['p(99)<2000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

function headers(userId) {
  return {
    headers: { 'Content-Type': 'application/json', 'X-USER-ID': String(userId) },
  };
}

export function payment() {
  const userId = 1 + (__VU % 1000);
  const productId = productIds[Math.floor(Math.random() * productIds.length)];

  const created = http.post(
    `${BASE}/api/v1/orders`,
    JSON.stringify({ lines: [{ productId, quantity: 1 }] }),
    { ...headers(userId), tags: { step: 'create' } },
  );
  createLatency.add(created.timings.duration);

  if (created.status !== 200) {
    createRejected.add(1);
    return;
  }

  const orderId = JSON.parse(created.body).data.id;
  const confirmed = http.post(
    `${BASE}/api/v1/orders/${orderId}/confirm`,
    JSON.stringify({ cardType: CARD_TYPE, cardNo: CARD_NO }),
    { ...headers(userId), tags: { step: 'confirm', name: 'POST /api/v1/orders/{id}/confirm' } },
  );
  confirmLatency.add(confirmed.timings.duration);

  if (confirmed.status === 200) {
    confirmOk.add(1);
  } else if (confirmed.status >= 400 && confirmed.status < 500) {
    confirmRejected.add(1);
  } else {
    confirmBroken.add(1);
  }
}

export function heavy() {
  const res = http.get(`${BASE}/api/v1/products?page=0&size=20`, {
    tags: { step: 'heavy' },
  });
  heavyLatency.add(res.timings.duration);

  check(res, { 'heavy 200': (r) => r.status === 200 });
}

export function light() {
  const res = http.get(`${BASE}/api/v1/__ping`, { tags: { step: 'light' } });
  lightLatency.add(res.timings.duration);

  check(res, { 'light 404': (r) => r.status === 404 });
}

export function handleSummary(data) {
  const out = { stdout: '\n' };
  if (OUT) {
    out[OUT] = JSON.stringify(
      { vus: VUS, duration: DURATION, iterations: ITERATIONS, metrics: data.metrics },
      null,
      2,
    );
  }

  return out;
}
