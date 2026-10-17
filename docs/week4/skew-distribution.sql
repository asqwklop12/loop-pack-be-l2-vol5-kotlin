-- 분포 교정 — 행 수는 100만 그대로, 값의 분포만 바꾼다.
--
-- seed-large.sql 이 만든 데이터는 양은 맞지만 분포가 현실과 달랐다.
-- 가격이 200종류, 브랜드마다 정확히 1만개, 판매중지와 삭제가 0건이라
-- "어떤 브랜드에서는 인덱스가 안 먹는다" 같은 현상이 아예 나타나지 않았다.
--
-- 실행: docker compose -f docker/infra-compose.yml exec -T mysql \
--         mysql -uroot -proot loopers < docs/week4/skew-distribution.sql
-- 소요: 약 22초
--
-- 되돌리기: seed-large.sql 을 다시 돌린다 (TRUNCATE 후 재생성).

SET SESSION cte_max_recursion_depth = 1000;
SET autocommit = 0;

-- ---------------------------------------------------------------- 상품
-- 1) 브랜드를 편중시킨다. 균등 1만개씩 → 큰 브랜드와 작은 브랜드.
--    u^3 분포라 앞 번호에 몰린다. brand 1 이 21.6%, brand 100 이 0.33%.
UPDATE product
   SET brand_id = 1 + FLOOR(POW(CRC32(id * 31) / 4294967295, 3) * 100);
COMMIT;

-- 2) 가격을 흩뿌린다. 200종류 → 10만종류.
--    동점이 5,000행씩이면 tie-break 효과가 비현실적으로 크게 나온다.
UPDATE product
   SET price = price + (CRC32(id * 29) % 1000);
COMMIT;

-- 3) 등록 시각을 1년에 걸쳐 흩뿌린다.
--    시드는 브랜드 묶음마다 NOW() 를 써서 1만개가 같은 값이었다. latest 정렬이 전부 동점이 된다.
UPDATE product
   SET created_at = created_at - INTERVAL (CRC32(id * 23) % 31536000) SECOND;
COMMIT;

-- 4) 10% 를 판매중지로. status 가 한 값뿐이면 그 컬럼의 선택도를 볼 수 없다.
UPDATE product
   SET status = 'SUSPENDED', updated_at = NOW(6)
 WHERE CRC32(id * 13) % 10 = 0;
COMMIT;

-- 5) 5% 를 삭제로. deleted_at 이 전부 NULL 이면 같은 문제가 생긴다.
UPDATE product
   SET deleted_at = NOW(6) - INTERVAL (CRC32(id * 19) % 2592000) SECOND,
       updated_at = NOW(6)
 WHERE CRC32(id * 17) % 20 = 0;
COMMIT;

-- ---------------------------------------------------------------- 좋아요
DROP TEMPORARY TABLE IF EXISTS n500;
CREATE TEMPORARY TABLE n500 (n INT PRIMARY KEY);
INSERT INTO n500
WITH RECURSIVE r(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM r WHERE i < 499) SELECT i FROM r;
COMMIT;

-- 6) 좋아요를 편중시킨다. 최대 4개 → 소수의 인기 상품이 수백개를 갖는다.
--    500 상품 중 1 개(약 2,000 상품)에 50~499 개를 더 넣는다.
--    현실의 좋아요는 멱법칙에 가깝고, 균등 0~4 로는 likes_desc 상위권이 전부 동점이 된다.
INSERT INTO product_like (created_at, updated_at, user_id, product_id)
SELECT NOW(6) - INTERVAL (CRC32(p.id * 43 + j.n) % 7776000) SECOND, NOW(6),
       100000 + (p.id * 7 + j.n) % 500000, p.id
  FROM product p
  JOIN n500 j ON j.n < (50 + CRC32(p.id * 41) % 450)
 WHERE CRC32(p.id * 37) % 500 = 0;
COMMIT;

-- ---------------------------------------------------------------- 브랜드
-- 7) 브랜드 5개를 삭제하고 그 상품을 판매중지로. 앱의 브랜드 삭제 동작과 같게 맞춘다.
UPDATE brand SET deleted_at = NOW(6), updated_at = NOW(6) WHERE id BETWEEN 96 AND 100;
UPDATE product p SET p.status = 'SUSPENDED', p.updated_at = NOW(6)
 WHERE p.brand_id BETWEEN 96 AND 100 AND p.status = 'SELLING';
COMMIT;

SET autocommit = 1;
DROP TEMPORARY TABLE n500;

-- ---------------------------------------------------------------- 통계 갱신
-- 분포가 바뀌었으니 옵티마이저가 쓰는 통계도 새로 떠야 한다.
ANALYZE TABLE product, product_like, brand;

-- ---------------------------------------------------------------- 결과
SELECT COUNT(*)                                             AS 상품,
       COUNT(DISTINCT price)                                AS 가격종류,
       COUNT(DISTINCT created_at)                           AS 등록시각종류,
       ROUND(SUM(status = 'SUSPENDED') / COUNT(*) * 100, 2) AS `판매중지%`,
       ROUND(SUM(deleted_at IS NOT NULL) / COUNT(*) * 100, 2) AS `삭제%`
  FROM product;
