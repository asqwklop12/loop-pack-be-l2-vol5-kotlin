package com.loopers.infrastructure.campaign

import com.loopers.domain.campaign.Campaign
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CampaignJpaRepository : JpaRepository<Campaign, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Campaign?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id and c.deletedAt is null")
    fun findByIdForUpdate(id: Long): Campaign?
}
