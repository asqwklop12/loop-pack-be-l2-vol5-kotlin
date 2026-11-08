package com.loopers.infrastructure.campaign

import com.loopers.domain.campaign.Campaign
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface CampaignJpaRepository : JpaRepository<Campaign, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Campaign?

    @Modifying
    @Query(
        """
        update Campaign c
           set c.issuedCount = c.issuedCount + 1,
               c.updatedAt = :now
         where c.id = :id
           and c.issuedCount + 1 <= c.totalLimit
        """,
    )
    fun increaseIssuedCountWithinLimit(@Param("id") id: Long, @Param("now") now: ZonedDateTime): Int
}
