SET SESSION cte_max_recursion_depth = 10000;

SET autocommit = 0;
SET unique_checks = 0;

-- 숫자 10,000개
DROP TEMPORARY TABLE IF EXISTS nums;
CREATE TEMPORARY TABLE nums (n INT PRIMARY KEY);
INSERT INTO nums
WITH RECURSIVE r(i) AS (SELECT 0 UNION ALL SELECT i+1 FROM r WHERE i < 9999) SELECT i FROM r;


-- 브랜드 100개
INSERT INTO brand (created_at, updated_at, name)
SELECT NOW(6), NOW(6), CONCAT('브랜드', LPAD(n+1, 3, '0')) FROM nums WHERE n < 100;
COMMIT;




-- 상품 100만개 — 브랜드당 1만개씩, 브랜드 단위로 커밋
DELIMITER $$
CREATE PROCEDURE seed_products()
BEGIN
    DECLARE b INT DEFAULT 0;
    DECLARE base BIGINT;
    SELECT MIN(id) INTO base FROM brand;
    WHILE b < 100 DO
            INSERT INTO product (created_at, updated_at, brand_id, name, price, stock, status, version)
            SELECT NOW(6), NOW(6), base + b,
                   CONCAT('상품', LPAD(b * 10000 + n + 1, 7, '0')),
                   1000 + (n % 200) * 500, n % 100, 'SELLING', 0
            FROM nums;
            COMMIT;
            SET b = b + 1;
        END WHILE;
END$$
DELIMITER ;
CALL seed_products();

-- 좋아요 약 100만개 — 상품 절반은 0개, 나머지는 0~4개. 5만 건씩 끊어 커밋
DELIMITER $$
CREATE PROCEDURE seed_likes()
BEGIN
    DECLARE lo BIGINT; DECLARE hi BIGINT; DECLARE mx BIGINT;
    SELECT MIN(id), MAX(id) INTO lo, mx FROM product;
    WHILE lo <= mx DO
            SET hi = lo + 49999;
            INSERT INTO product_like (created_at, updated_at, user_id, product_id)
            SELECT NOW(6), NOW(6), 1 + ((p.id * 7 + j.n) % 100000), p.id
            FROM product p
                     JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2
                           UNION ALL SELECT 3 UNION ALL SELECT 4) j
                          ON j.n < IF(CRC32(p.id) % 2 = 0, CRC32(p.id * 31) % 5, 0)
            WHERE p.id BETWEEN lo AND hi;
            COMMIT;
            SET lo = hi + 1;
        END WHILE;
END$$
DELIMITER ;
CALL seed_likes();

SET unique_checks = 1;
SET autocommit = 1;
DROP PROCEDURE seed_likes;



select count(*) from product_like;
