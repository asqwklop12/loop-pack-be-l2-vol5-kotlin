package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStats
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductStatsJpaRepository : JpaRepository<ProductStats, Long> {
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductStats>

    @Modifying
    @Query(value = "DELETE FROM product_stats", nativeQuery = true)
    fun deleteAllRows(): Int

    /**
     * 지우고 다시 채우는 이유는 좋아요가 하드 삭제이기 때문이다. 갱신만 하면 좋아요가 전부
     * 취소된 상품이 예전 값으로 남는다. 둘을 한 트랜잭션에 묶어 두면 읽는 쪽은 비어 있는
     * 순간을 보지 않는다.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO product_stats (product_id, like_count, created_at, updated_at)
            SELECT product_id, COUNT(*), NOW(6), NOW(6)
            FROM product_like
            GROUP BY product_id
        """,
        nativeQuery = true,
    )
    fun insertAllFromLikes(): Int
}
