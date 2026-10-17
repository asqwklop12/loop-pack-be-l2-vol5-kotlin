-- day3 — day2 에서 6개월이 더 지난 상태. 신규 브랜드가 입주했다.
--
-- 기준 시각 2027-10-14. day2 데이터를 복사해 둔 뒤 이 스크립트를 돌린다.
--
-- 이번 회차의 사건
--   신규 브랜드 10개(id 101~110)가 2027-04 ~ 2027-10 사이에 입주했다.
--   그 브랜드 상품은 어리지만 인기가 나이에 비해 좋다 — 인기도 점수에 2.4배 가중을 준다.
--   선점 효과만 있던 day1·day2 와 달리, 신규가 상위권에 끼어든다.
--
-- 바뀌는 것
--   브랜드    100 → 110      (살아 있는 브랜드 95 → 105)
--   상품      150만 → 200만  (기존 브랜드 35만 + 신규 브랜드 15만)
--   좋아요    400만 → 약 600만
--   판매중지  18.6% → 약 22%
--   삭제      8.0%  → 약 11%
--
-- 실행: docker exec -i mysql-day3 mysql -uroot -proot loopers < docs/week4/day3-aging.sql

SET SESSION cte_max_recursion_depth = 20000;
SET autocommit = 0;
SET unique_checks = 0;

-- ---------------------------------------------------------------- 1) 브랜드 10개 입주
-- 이름은 엔티티 규칙(4~8자)을 지킨다.
INSERT INTO brand (id, created_at, updated_at, name)
SELECT 100 + n,
       TIMESTAMP('2027-04-14') + INTERVAL (n * 17 + 3) DAY,
       TIMESTAMP('2027-04-14') + INTERVAL (n * 17 + 3) DAY,
       CONCAT('신규브랜드', LPAD(n, 2, '0'))
  FROM (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
        UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        UNION ALL SELECT 10) t;
COMMIT;

-- ---------------------------------------------------------------- 2) 기존 브랜드 신규 상품 35만
-- 살아 있는 브랜드(1~95)에만 들어간다. 등록 시각은 지난 6개월(183일)에 흩어진다.
INSERT INTO product (created_at, updated_at, brand_id, name, price, stock, status, version)
SELECT TIMESTAMP('2027-04-14') + INTERVAL (CRC32(s.id * 83) % 15811200) SECOND,
       TIMESTAMP('2027-04-14') + INTERVAL (CRC32(s.id * 83) % 15811200) SECOND,
       1 + FLOOR(POW(CRC32(s.id * 89) / 4294967295, 3) * 95),
       CONCAT('추가상품', LPAD(s.id, 7, '0')),
       GREATEST(1, ROUND((1000 + (CRC32(s.id * 97) % 100000))
                         * (0.5 + (CRC32((1 + FLOOR(POW(CRC32(s.id * 89) / 4294967295, 3) * 95)) * 101)
                                   / 4294967295) * 1.5))),
       ROUND(POW(CRC32(s.id * 103) / 4294967295, 2.4) * 400),
       'SELLING',
       0
  FROM (SELECT id FROM product ORDER BY id LIMIT 350000) s;
COMMIT;

-- ---------------------------------------------------------------- 3) 신규 브랜드 상품 15만
-- 브랜드마다 8,000 ~ 23,000 개. 입주 시점 이후에만 등록된다.
INSERT INTO product (created_at, updated_at, brand_id, name, price, stock, status, version)
SELECT b.created_at
       + INTERVAL (CRC32(s.id * 107 + b.id) % GREATEST(TIMESTAMPDIFF(SECOND, b.created_at, TIMESTAMP('2027-10-14')), 1)) SECOND,
       TIMESTAMP('2027-10-14'),
       b.id,
       CONCAT('입주상품', LPAD(s.id, 6, '0'), '-', b.id - 100),
       GREATEST(1, ROUND((1000 + (CRC32(s.id * 109 + b.id) % 100000))
                         * (0.5 + (CRC32(b.id * 101) / 4294967295) * 1.5))),
       ROUND(POW(CRC32(s.id * 113 + b.id) / 4294967295, 2.4) * 400),
       'SELLING',
       0
  FROM brand b
  JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM product WHERE id <= 1631917) s
    ON s.rn <= 8000 + (CRC32(b.id * 127) % 15000)
 WHERE b.id BETWEEN 101 AND 110;
COMMIT;

-- ---------------------------------------------------------------- 4) 6개월 더 늙은 만큼 판매중지·삭제
-- 임계값이 나이에 따라 오른다. 이미 중지·삭제된 것은 그대로 둔다.
UPDATE product
   SET status = 'SUSPENDED', updated_at = TIMESTAMP('2027-10-14')
 WHERE status = 'SELLING'
   AND deleted_at IS NULL
   AND (CRC32(id * 13) / 4294967295)
       < (0.05 + 0.26 * LEAST(DATEDIFF('2027-10-14', created_at) / 730, 1));
COMMIT;

UPDATE product
   SET deleted_at = created_at
       + INTERVAL (CRC32(id * 19)
                   % GREATEST(TIMESTAMPDIFF(SECOND, created_at, TIMESTAMP('2027-10-14')), 1)) SECOND,
       updated_at = TIMESTAMP('2027-10-14')
 WHERE deleted_at IS NULL
   AND (CRC32(id * 17) / 4294967295)
       < (0.015 + 0.14 * LEAST(DATEDIFF('2027-10-14', created_at) / 730, 1));
COMMIT;

-- 삭제된 브랜드(96~100)의 상품은 계속 판매중지 상태를 유지한다.
UPDATE product
   SET status = 'SUSPENDED', updated_at = TIMESTAMP('2027-10-14')
 WHERE brand_id BETWEEN 96 AND 100 AND deleted_at IS NULL AND status = 'SELLING';
COMMIT;

-- ---------------------------------------------------------------- 5) 좋아요 추가
-- day3 목표 보유량을 200만 상품에 다시 매기고 차이만 넣는다.
--   신규 브랜드(101~110) 상품에는 2.4배 가중을 준다. 어린데도 상위권에 든다.
DROP TEMPORARY TABLE IF EXISTS pop3;
CREATE TEMPORARY TABLE pop3 (
  product_id BIGINT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  add_likes  INT NOT NULL,
  KEY k_add (add_likes)
);

INSERT INTO pop3 (product_id, created_at, add_likes)
SELECT r.product_id, r.created_at,
       GREATEST(ROUND(13035 * POW(r.rnk, -0.62)) - COALESCE(h.c, 0), 0)
  FROM (
    SELECT p.id AS product_id, p.created_at,
           ROW_NUMBER() OVER (ORDER BY
             (CRC32(p.id * 37) / 4294967295)
             * (0.35 + 0.65 * LEAST(DATEDIFF('2027-10-14', p.created_at) / 730, 1))
             * (CASE WHEN p.brand_id >= 101 THEN 1.00 ELSE 1.05 - 0.0055 * p.brand_id END)
             * (1.15 - 0.30 * LEAST(p.price, 200000) / 200000)
             * (CASE WHEN p.brand_id >= 101 THEN 2.40 ELSE 1.00 END)
             DESC) AS rnk
      FROM product p
  ) r
  LEFT JOIN (SELECT product_id, COUNT(*) c FROM product_like GROUP BY product_id) h
         ON h.product_id = r.product_id
 WHERE r.rnk <= 800000;
DELETE FROM pop3 WHERE add_likes = 0;
COMMIT;

DROP TEMPORARY TABLE IF EXISTS n14000;
CREATE TEMPORARY TABLE n14000 (n INT PRIMARY KEY);
INSERT INTO n14000
WITH RECURSIVE r(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM r WHERE i < 13999) SELECT i FROM r;
COMMIT;

-- 추가되는 좋아요는 day2 기준시각 이후에 생긴 것이다. 신규 상품은 자기 등록 이후부터.
INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2027-04-14'))
       + INTERVAL (CRC32(t.product_id * 131 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2027-04-14')), TIMESTAMP('2027-10-14')), 1)) SECOND,
       TIMESTAMP('2027-10-14'),
       1 + (t.product_id * 5431 + j.n) % 800000,
       t.product_id
  FROM pop3 t JOIN n14000 j ON j.n < t.add_likes
 WHERE t.add_likes >= 1000;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2027-04-14'))
       + INTERVAL (CRC32(t.product_id * 131 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2027-04-14')), TIMESTAMP('2027-10-14')), 1)) SECOND,
       TIMESTAMP('2027-10-14'),
       1 + (t.product_id * 5431 + j.n) % 800000,
       t.product_id
  FROM pop3 t JOIN n14000 j ON j.n < t.add_likes
 WHERE t.add_likes BETWEEN 100 AND 999;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2027-04-14'))
       + INTERVAL (CRC32(t.product_id * 131 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2027-04-14')), TIMESTAMP('2027-10-14')), 1)) SECOND,
       TIMESTAMP('2027-10-14'),
       1 + (t.product_id * 5431 + j.n) % 800000,
       t.product_id
  FROM pop3 t JOIN n14000 j ON j.n < t.add_likes
 WHERE t.add_likes BETWEEN 20 AND 99;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT GREATEST(t.created_at, TIMESTAMP('2027-04-14'))
       + INTERVAL (CRC32(t.product_id * 131 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND,
                       GREATEST(t.created_at, TIMESTAMP('2027-04-14')), TIMESTAMP('2027-10-14')), 1)) SECOND,
       TIMESTAMP('2027-10-14'),
       1 + (t.product_id * 5431 + j.n) % 800000,
       t.product_id
  FROM pop3 t JOIN n14000 j ON j.n < t.add_likes
 WHERE t.add_likes <= 19;
COMMIT;

SET unique_checks = 1;
SET autocommit = 1;
DROP TEMPORARY TABLE pop3;
DROP TEMPORARY TABLE n14000;

ANALYZE TABLE product, product_like, brand;
