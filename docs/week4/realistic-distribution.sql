-- 현실적 분포 재구성 — 행 수는 100만 그대로, 값과 관계를 다시 만든다.
--
-- skew-distribution.sql 이 모양은 고쳤지만 세 가지가 남았다.
--   1. 좋아요가 499 에서 잘렸다. 현실은 천장이 없다.
--   2. 좋아요가 가격·브랜드·나이와 아무 상관이 없었다.
--   3. 좋아요 65,912 개가 상품보다 먼저 생겼다. 시간순이 깨져 있었다.
--
-- 이 스크립트는 skew-distribution.sql 을 돌린 상태 위에 덮어쓴다.
-- brand_id 편중과 created_at 분산은 그대로 쓰고, 나머지를 다시 만든다.
--
-- 실행: docker compose -f docker/infra-compose.yml exec -T mysql \
--         mysql -uroot -proot loopers < docs/week4/realistic-distribution.sql

SET SESSION cte_max_recursion_depth = 10000;
SET autocommit = 0;
SET unique_checks = 0;

-- ---------------------------------------------------------------- 초기화
TRUNCATE product_like;
UPDATE product SET status = 'SELLING', deleted_at = NULL;
COMMIT;

-- ---------------------------------------------------------------- 상품
-- 1) 브랜드마다 가격대를 준다. 현실의 브랜드는 가격 구간을 점유한다.
--    (brand_id, price) 복합 인덱스가 브랜드마다 다르게 동작하는 원인이 된다.
UPDATE product
   SET price = GREATEST(1, ROUND(price * (0.5 + (CRC32(brand_id * 101) / 4294967295) * 1.5)));
COMMIT;

-- 2) 재고를 편중시킨다. 균등 0~99 → 대부분 적고 일부가 많다. 품절도 생긴다.
UPDATE product
   SET stock = ROUND(POW(CRC32(id * 53) / 4294967295, 2.4) * 400);
COMMIT;

-- 3) 판매중지를 나이와 상관있게 만든다. 오래된 상품이 더 많이 내려간다.
--    신상 4% → 1년 전 20%.
UPDATE product
   SET status = 'SUSPENDED', updated_at = UTC_TIMESTAMP()
 WHERE (CRC32(id * 13) / 4294967295)
       < (0.04 + 0.16 * LEAST(DATEDIFF(UTC_TIMESTAMP(), created_at) / 365, 1));
COMMIT;

-- 4) 삭제도 나이와 상관있게. 삭제 시점은 반드시 등록 이후다.
UPDATE product
   SET deleted_at = created_at
       + INTERVAL (CRC32(id * 19) % GREATEST(TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP()), 1)) SECOND,
       updated_at = UTC_TIMESTAMP()
 WHERE (CRC32(id * 17) / 4294967295)
       < (0.01 + 0.08 * LEAST(DATEDIFF(UTC_TIMESTAMP(), created_at) / 365, 1));
COMMIT;

-- ---------------------------------------------------------------- 브랜드
-- 5) 브랜드 5개를 삭제하고 그 상품을 판매중지로. 앱의 BrandFacade.deleteBrand 와 같은 조건.
UPDATE brand SET deleted_at = UTC_TIMESTAMP(), updated_at = UTC_TIMESTAMP() WHERE id BETWEEN 96 AND 100;
UPDATE product
   SET status = 'SUSPENDED', updated_at = UTC_TIMESTAMP()
 WHERE brand_id BETWEEN 96 AND 100
   AND deleted_at IS NULL
   AND status = 'SELLING';
COMMIT;

-- ---------------------------------------------------------------- 좋아요
-- 6) 인기도를 점수로 매긴다. 무작위에 세 가지 상관을 섞는다.
--      나이  — 오래된 상품이 좋아요를 더 모았다
--      브랜드 — 큰 브랜드의 상품이 더 눈에 띈다
--      가격  — 싼 상품이 조금 더 눌린다
DROP TEMPORARY TABLE IF EXISTS pop;
CREATE TEMPORARY TABLE pop (
  product_id   BIGINT PRIMARY KEY,
  created_at   DATETIME(6) NOT NULL,
  likes_target INT NOT NULL,
  KEY k_target (likes_target)
);

-- 7) 점수 순위를 멱법칙으로 좋아요 수에 대응시킨다. 천장이 없다.
--      rank 1 → 5,672개 · rank 1,000 → 78개 · rank 400,000 → 2개
--    상위 40만 상품만 좋아요를 받고 나머지 60만은 0개다.
INSERT INTO pop (product_id, created_at, likes_target)
SELECT product_id, created_at, GREATEST(ROUND(5672 * POW(rnk, -0.62)), 1)
  FROM (
    SELECT p.id AS product_id, p.created_at,
           ROW_NUMBER() OVER (ORDER BY
             (CRC32(p.id * 37) / 4294967295)
             * (0.35 + 0.65 * LEAST(DATEDIFF(UTC_TIMESTAMP(), p.created_at) / 365, 1))
             * (1.05 - 0.0055 * p.brand_id)
             * (1.15 - 0.30 * LEAST(p.price, 200000) / 200000)
             DESC) AS rnk
      FROM product p
  ) r
 WHERE rnk <= 400000;
COMMIT;

-- 숫자 6,000개
DROP TEMPORARY TABLE IF EXISTS n6000;
CREATE TEMPORARY TABLE n6000 (n INT PRIMARY KEY);
INSERT INTO n6000
WITH RECURSIVE r(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM r WHERE i < 5999) SELECT i FROM r;
COMMIT;

-- 8) 좋아요를 넣는다. 구간별로 끊어 커밋한다.
--    created_at 은 반드시 상품 등록 이후 ~ 지금 사이다. 시간순이 깨지지 않는다.
--    user_id 는 상품 안에서 겹치지 않는다 (n < 6000 << 사용자 풀 80만).
INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT t.created_at
       + INTERVAL (CRC32(t.product_id * 43 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND, t.created_at, UTC_TIMESTAMP()), 1)) SECOND,
       UTC_TIMESTAMP(),
       1 + (t.product_id * 7919 + j.n) % 800000,
       t.product_id
  FROM pop t JOIN n6000 j ON j.n < t.likes_target
 WHERE t.likes_target >= 1000;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT t.created_at
       + INTERVAL (CRC32(t.product_id * 43 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND, t.created_at, UTC_TIMESTAMP()), 1)) SECOND,
       UTC_TIMESTAMP(),
       1 + (t.product_id * 7919 + j.n) % 800000,
       t.product_id
  FROM pop t JOIN n6000 j ON j.n < t.likes_target
 WHERE t.likes_target BETWEEN 100 AND 999;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT t.created_at
       + INTERVAL (CRC32(t.product_id * 43 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND, t.created_at, UTC_TIMESTAMP()), 1)) SECOND,
       UTC_TIMESTAMP(),
       1 + (t.product_id * 7919 + j.n) % 800000,
       t.product_id
  FROM pop t JOIN n6000 j ON j.n < t.likes_target
 WHERE t.likes_target BETWEEN 20 AND 99;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT t.created_at
       + INTERVAL (CRC32(t.product_id * 43 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND, t.created_at, UTC_TIMESTAMP()), 1)) SECOND,
       UTC_TIMESTAMP(),
       1 + (t.product_id * 7919 + j.n) % 800000,
       t.product_id
  FROM pop t JOIN n6000 j ON j.n < t.likes_target
 WHERE t.likes_target BETWEEN 5 AND 19;
COMMIT;

INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT t.created_at
       + INTERVAL (CRC32(t.product_id * 43 + j.n)
                   % GREATEST(TIMESTAMPDIFF(SECOND, t.created_at, UTC_TIMESTAMP()), 1)) SECOND,
       UTC_TIMESTAMP(),
       1 + (t.product_id * 7919 + j.n) % 800000,
       t.product_id
  FROM pop t JOIN n6000 j ON j.n < t.likes_target
 WHERE t.likes_target <= 4;
COMMIT;

SET unique_checks = 1;
SET autocommit = 1;
DROP TEMPORARY TABLE pop;
DROP TEMPORARY TABLE n6000;

ANALYZE TABLE product, product_like, brand;
