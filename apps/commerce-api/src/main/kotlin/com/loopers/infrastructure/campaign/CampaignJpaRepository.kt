package com.loopers.infrastructure.campaign

import com.loopers.domain.campaign.Campaign
import org.springframework.data.jpa.repository.JpaRepository

interface CampaignJpaRepository : JpaRepository<Campaign, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Campaign?
}
