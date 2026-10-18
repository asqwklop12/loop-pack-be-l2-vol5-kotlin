-- 부하 테스트가 주문할 상품 id 를 뽑는다.
-- 재고가 넉넉한 상품에 넓게 퍼뜨려야 재고 부족과 같은 행 경쟁이 섞이지 않는다.
--
--   docker exec docker-mysql-1 sh -c 'mysql -uroot -proot -D loopers -N -e "
--     select id from product where status=\"SELLING\" and deleted_at is null and stock >= 50 limit 20000
--   "' | python3 -c 'import sys,json;print(json.dumps([int(l) for l in sys.stdin if l.strip()]))' > ids.json
select id
from product
where status = 'SELLING'
  and deleted_at is null
  and stock >= 50
limit 20000;
