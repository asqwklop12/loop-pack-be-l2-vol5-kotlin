-- id 순서 교정 + 중복 좋아요 제거
--
-- 두 가지가 현실과 달랐다.
--
--  1. id 와 created_at 이 어긋났다.
--     seed-large.sql 이 NOW() 로 넣은 뒤 created_at 을 사후에 흩뿌렸기 때문이다.
--     AUTO_INCREMENT 인 id 는 현실에서 등록 순서와 반드시 같이 오른다.
--     어긋나면 `ORDER BY created_at DESC, id DESC` 의 tie-break 전제가 거짓이 되고,
--     created_at 인덱스로 테이블을 되짚을 때 디스크상 인접성이 사라져
--     latest 정렬의 인덱스 효과가 실제보다 나쁘게 측정된다.
--
--  2. 같은 (user_id, product_id) 쌍이 중복으로 들어갔다.
--     uk_like_user_product 를 지운 상태에서 day2·day3 의 user_id 계산식이
--     기존 값과 겹쳤다. 현실에서는 제약이 막는다.
--
-- 방법: created_at 순으로 id 를 다시 매긴 테이블을 만들고 통째로 교체한다.
--       product_like.product_id 는 매핑으로 갱신하면서 중복을 함께 접는다.
--
-- 주의: 유니크 제약은 되살리지 않는다. 인덱스 구성을 직접 고르기 위해 지운 상태를 유지한다.
--       상품명에 박힌 숫자는 새 id 와 맞지 않게 된다 (원래도 의미 없는 값이다).
--
-- 실행 (세 DB 각각):
--   docker compose -f docker/infra-compose.yml exec -T mysql mysql -uroot -proot loopers < docs/week4/fix-id-ordering.sql
--   docker exec -i mysql-day2 mysql -uroot -proot loopers < docs/week4/fix-id-ordering.sql
--   docker exec -i mysql-day3 mysql -uroot -proot loopers < docs/week4/fix-id-ordering.sql

SET autocommit = 0;
SET unique_checks = 0;

-- ---------------------------------------------------------------- 1) 매핑
-- created_at 이 같으면 기존 id 순으로 가른다. 결과는 결정적이다.
DROP TABLE IF EXISTS id_map;
CREATE TABLE id_map (
  old_id BIGINT NOT NULL PRIMARY KEY,
  new_id BIGINT NOT NULL,
  KEY k_new (new_id)
) ENGINE = InnoDB;

INSERT INTO id_map (old_id, new_id)
SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) FROM product;
COMMIT;

-- ---------------------------------------------------------------- 2) 상품 재배치
DROP TABLE IF EXISTS product_new;
CREATE TABLE product_new LIKE product;

INSERT INTO product_new
       (id, created_at, updated_at, deleted_at, brand_id, name, price, stock, status, version)
SELECT m.new_id, p.created_at, p.updated_at, p.deleted_at,
       p.brand_id, p.name, p.price, p.stock, p.status, p.version
  FROM product p JOIN id_map m ON m.old_id = p.id;
COMMIT;

-- ---------------------------------------------------------------- 3) 좋아요 재배치 + 중복 접기
-- GROUP BY 가 (user_id, product_id) 중복을 하나로 만든다. 가장 먼저 누른 시점을 남긴다.
DROP TABLE IF EXISTS product_like_new;
CREATE TABLE product_like_new LIKE product_like;

INSERT INTO product_like_new (created_at, updated_at, user_id, product_id)
SELECT MIN(l.created_at), MAX(l.updated_at), l.user_id, m.new_id
  FROM product_like l JOIN id_map m ON m.old_id = l.product_id
 GROUP BY l.user_id, m.new_id;
COMMIT;

-- ---------------------------------------------------------------- 4) 교체
DROP TABLE IF EXISTS product_old;
DROP TABLE IF EXISTS product_like_old;
RENAME TABLE product      TO product_old,      product_new      TO product;
RENAME TABLE product_like TO product_like_old, product_like_new TO product_like;

DROP TABLE product_old;
DROP TABLE product_like_old;
DROP TABLE id_map;

SET unique_checks = 1;
SET autocommit = 1;

ANALYZE TABLE product, product_like;

-- ---------------------------------------------------------------- 5) 확인
SELECT (SELECT COUNT(*) FROM product)      AS products,
       (SELECT COUNT(*) FROM product_like) AS likes,
       (SELECT COUNT(*) FROM (SELECT user_id, product_id FROM product_like
                               GROUP BY user_id, product_id HAVING COUNT(*) > 1) d) AS dup_pairs,
       (SELECT COUNT(*) FROM (SELECT LAG(created_at) OVER (ORDER BY id) AS prev, created_at
                                FROM product) w
         WHERE w.prev > w.created_at) AS order_violations;
