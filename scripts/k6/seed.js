import http from 'k6/http';
import { check, fail } from 'k6';

// 브랜드 10개, 브랜드당 상품 10개, 주문 2~3개를 만든다.
//   k6 run scripts/k6/seed.js
//   BASE_URL=http://localhost:8080 k6 run scripts/k6/seed.js

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USER_ID = __ENV.USER_ID || '1';

const BRAND_COUNT = 10;
const PRODUCT_PER_BRAND = 10;
const ORDER_COUNT = 3;

export const options = { vus: 1, iterations: 1 };

const admin = { headers: { 'Content-Type': 'application/json', 'X-USER-ID': USER_ID, 'X-USER-ROLE': 'ADMIN' } };
const customer = { headers: { 'Content-Type': 'application/json', 'X-USER-ID': USER_ID } };

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pickDistinct(list, count) {
  const pool = [...list];
  const picked = [];
  for (let i = 0; i < count && pool.length > 0; i++) {
    picked.push(pool.splice(randomInt(0, pool.length - 1), 1)[0]);
  }
  return picked;
}

function post(path, body, opts) {
  return http.post(`${BASE}${path}`, JSON.stringify(body), opts);
}

function put(path, body, opts) {
  return http.put(`${BASE}${path}`, JSON.stringify(body), opts);
}

function dataOf(res, label) {
  if (res.status < 200 || res.status >= 300) {
    fail(`${label} 실패 [${res.status}] ${res.body}`);
  }
  return JSON.parse(res.body).data;
}

export default function () {
  // 1. 브랜드 — 이름은 4~8자여야 한다
  const brandIds = [];
  for (let b = 1; b <= BRAND_COUNT; b++) {
    const res = post('/api-admin/v1/brands', { name: `브랜드${String(b).padStart(2, '0')}` }, admin);
    brandIds.push(dataOf(res, '브랜드 생성').id);
  }
  console.log(`브랜드 ${brandIds.length}개`);

  // 2. 상품 — 생성 시 재고 0이므로 따로 채운다
  const productIds = [];
  for (let i = 0; i < brandIds.length; i++) {
    for (let p = 1; p <= PRODUCT_PER_BRAND; p++) {
      const created = post(
        '/api-admin/v1/products',
        { brandId: brandIds[i], name: `상품${i + 1}-${p}`, price: randomInt(1, 100) * 1000 },
        admin,
      );
      const productId = dataOf(created, '상품 생성').id;

      const stocked = put(`/api-admin/v1/products/${productId}/stock`, { amount: randomInt(10, 100) }, admin);
      dataOf(stocked, '재고 변경');

      productIds.push(productId);
    }
  }
  console.log(`상품 ${productIds.length}개`);

  // 3. 포인트 — 주문 합계보다 넉넉히
  const charged = post('/api/v1/points/charge', { amount: 100000000 }, customer);
  console.log(`포인트 충전 후 잔액 ${dataOf(charged, '포인트 충전').balance}`);

  // 4. 주문 — 같은 상품이 두 번 들어가면 거절되므로 서로 다른 상품을 고른다
  for (let o = 1; o <= ORDER_COUNT; o++) {
    const lines = pickDistinct(productIds, randomInt(1, 3)).map((productId) => ({
      productId,
      quantity: randomInt(1, 3),
    }));

    const created = post('/api/v1/orders', { lines }, customer);
    const order = dataOf(created, '주문 생성');

    // 마지막 하나는 DRAFT 로 남겨 둔다
    if (o < ORDER_COUNT) {
      const confirmed = post(`/api/v1/orders/${order.id}/confirm`, null, customer);
      const result = dataOf(confirmed, '주문 확정');
      console.log(`주문 ${order.id} ${result.status} 결제액 ${result.paidAmount} (품목 ${lines.length})`);
    } else {
      console.log(`주문 ${order.id} ${order.status} 합계 ${order.totalAmount} (품목 ${lines.length})`);
    }
  }

  check(null, { '시드 완료': () => true });
}
