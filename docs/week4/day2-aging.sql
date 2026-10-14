-- day2 — day1 에서 6개월이 지난 상태.
--
-- day1 의 기준 시각은 2026-10-14, day2 는 2027-04-14 다.
-- day1 데이터를 그대로 복사해 둔 뒤 이 스크립트를 돌린다. 상품 id 가 유지되므로
-- "같은 시스템의 나중 모습" 이 된다 (재생성이 아니다).
--
-- 바뀌는 것
--   상품      100만 → 150만   (6개월간 50만 신규)
--   좋아요    198만 → 약 400만 (6개월간 추가, 인기 상품에 더 몰린다)
--   판매중지  13.6% → 약 18%   (나이와 상관있으므로 자연히 오른다)
--   삭제      5.1%  → 약 8%
--
-- 유지되는 것
--   이미 판매중지·삭제된 상품은 그 상태와 시점을 그대로 둔다 (단조 증가).
--   신규 상품은 살아 있는 브랜드(1~95)에만 들어간다. 삭제된 브랜드에는 상품을 못 만든다.
--
-- 실행: docker exec -i mysql-day2 mysql -uroot -proot loopers < docs/week4/day2-aging.sql

SET SESSION cte_max_recursion_depth = 10000;
SET autocommit = 0;
SET unique_checks = 0;

-- ---------------------------------------------------------------- 1) 신규 상품 50만
-- 기존 상품 id 를 난수원으로 써서 50만 행을 만든다. 따로 숫자 테이블을 만들 필요가 없다.
-- 등록 시각은 day1 기준시각 ~ day2 기준시각 사이(184일)에 흩어진다.
INSERT INTO product (created_at, updated_at, brand_id, name, price, stock, status, version)
SELECT TIMESTAMP('2026-10-14') + INTERVAL (CRC32(s.id * 61) % 15897600) SECOND,
       TIMESTAMP('2026-10-14') + INTERVAL (CRC32(s.id * 61) % 15897600) SECOND,
       b.brand_id,
       CONCAT('신상품', LPAD(s.id, 7, '0')),
       GREATEST(1, ROUND((1000 + (CRC32(s.id * 73) % 100000))
                         * (0.5 + (CRC32(b.brand_id * 101) / 4294967295) * 1.5))),
       ROUND(POW(CRC32(s.id * 79) / 4294967295, 2.4) * 400),
       'SELLING',
       0
  FROM (SELECT id FROM product ORDER BY id LIMIT 500000) s
  JOIN (SELECT id, 1 + FLOOR(POW(CRC32(id * 71) / 4294967295, 3) * 95) AS brand_id FROM product) b
    ON b.id = s.id;
COMMIT;

-- ---------------------------------------------------------------- 2) 6개월 더 늙은 만큼 판매중지
-- 이미 중지된 것은 건드리지 않는다. 임계값이 나이에 따라 오르므로 같은 상품은 계속 중지 상태다.
UPDATE product
   SET status = 'SUSPENDED', updated_at = TIMESTAMP('2027-04-14')
 WHERE status = 'SELLING'
   AND deleted_at IS NULL
   AND (CRC32(id * 13) / 4294967295)
       < (0.05 + 0.26 * LEAST(DATEDIFF('2027-04-14', created_at) / 547, 1));
COMMIT;

-- ---------------------------------------------------------------- 3) 6개월 더 늙은 만큼 삭제
-- 이미 삭제된 상품의 deleted_at 은 유지한다. 삭제 시점은 등록 이후 ~ day2 기준시각 사이다.
UPDATE product
   SET deleted_at = created_at
       + INTERVAL (CRC32(id * 19)
                   % GREATEST(TIMESTAMPDIFF(SECOND, created_at, TIMESTAMP('2027-04-14')), 1)) SECOND,
       updated_at = TIMESTAMP('2027-04-14')
 WHERE deleted_at IS NULL
   AND (CRC32(id * 17) / 4294967295)
       < (0.015 + 0.13 * LEAST(DATEDIFF('2027-04-14', created_at) / 547, 1));
COMMIT;

-- ---------------------------------------------------------------- 4) 좋아요 추가
-- day2 시점의 목표 보유량을 150만 상품 전체에 다시 매기고, 이미 가진 수와의 차이만 넣는다.
-- 나이 가중이 있으므로 기존 상품이 상위를 유지한다 — 현실의 선점 효과와 같다.
DROP TEMPORARY TABLE IF EXISTS pop2;
CREATE TEMPORARY TABLE pop2 (
  product_id BIGINT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  add_likes  INT NOT NULL,
  KEY k_add (add_likes)
);

INSERT INTO pop2 (product_id, created_at, add_likes)
SELECT r.product_id, r.created_at,
       GREATEST(ROUND(9688 * POW(r.rnk, -0.62)) - COALESCE(h.c, 0), 0)
  FROM (
    SELECT p.id AS product_id, p.created_at,
           ROW_NUMBER() OVER (ORDER BY
             (CRC32(p.id * 37) / 4294967295)
             * (0.35 + 0.65 * LEAST(DATEDIFF('2027-04-14', p.created_at) / 547, 1))
             * (1.05 - 0.0055 * p.brand_id)
             * (1.15 - 0.30 * LEAST(p.price, 200000) / 200000)
             DESC) AS rnk
      FROM product p
  ) r
  LEFT JOIN (SELECT product_id, COUNT(*) c FROM product_like GROUP BY product_id) h
         ON h.product_id = r.product_id
 WHERE r.rnk <= 600000;
DELETE FROM pop2 WHERE add_likes = 0;
COMMIT;

-- 숫자 10,000개
DROP TEMPORARY TABLE IF EXISTS n10000;
CREATE TEMPORARY TABLE n10000 (n INT PRIMARY KEY);
INSERT INTO n10000
WITH RECURSIVE r(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM r WHERE i < 9999) SELECT i FROM r;
COMMIT;

-- 추가되는 좋아요는 모두 day1 기준시각 이후에 생긴 것이다.
-- 신규 상품은 자기 등록 이후부터. 그래서 시간순이 깨지지 않는다.
--   user_id 는 기존 좋아요와 겹칠 수 있다. uk_like_user_product 가 없는 상태라 허용된다.
INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2026-10-14'))
       + INTERVAL (CRC32(t.product_id * 91 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2026-10-14')), TIMESTAMP('2027-04-14')), 1)) SECOND,
       TIMESTAMP('2027-04-14'),
       1 + (t.product_id * 6271 + j.n) % 800000,
       t.product_id
  FROM pop2 t JOIN n10000 j ON j.n < t.add_likes
 WHERE t.add_likes >= 1000;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2026-10-14'))
       + INTERVAL (CRC32(t.product_id * 91 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2026-10-14')), TIMESTAMP('2027-04-14')), 1)) SECOND,
       TIMESTAMP('2027-04-14'),
       1 + (t.product_id * 6271 + j.n) % 800000,
       t.product_id
  FROM pop2 t JOIN n10000 j ON j.n < t.add_likes
 WHERE t.add_likes BETWEEN 100 AND 999;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2026-10-14'))
       + INTERVAL (CRC32(t.product_id * 91 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2026-10-14')), TIMESTAMP('2027-04-14')), 1)) SECOND,
       TIMESTAMP('2027-04-14'),
       1 + (t.product_id * 6271 + j.n) % 800000,
       t.product_id
  FROM pop2 t JOIN n10000 j ON j.n < t.add_likes
 WHERE t.add_likes BETWEEN 20 AND 99;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2026-10-14'))
       + INTERVAL (CRC32(t.product_id * 91 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2026-10-14')), TIMESTAMP('2027-04-14')), 1)) SECOND,
       TIMESTAMP('2027-04-14'),
       1 + (t.product_id * 6271 + j.n) % 800000,
       t.product_id
  FROM pop2 t JOIN n10000 j ON j.n < t.add_likes
 WHERE t.add_likes <= 19;
COMMIT;

SET unique_checks = 1;
SET autocommit = 1;
DROP TEMPORARY TABLE pop2;
DROP TEMPORARY TABLE n10000;

ANALYZE TABLE product, product_like, brand;
