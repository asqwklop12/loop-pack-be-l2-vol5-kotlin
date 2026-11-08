package com.loopers.infrastructure.campaign

import com.loopers.domain.campaign.Campaign
import com.loopers.domain.campaign.CampaignRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CampaignRepositoryImpl(
    private val campaignJpaRepository: CampaignJpaRepository,
) : CampaignRepository {
    override fun save(campaign: Campaign): Campaign = campaignJpaRepository.save(campaign)

    override fun find(id: Long): Campaign? = campaignJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun increaseIssuedCountWithinLimit(id: Long, now: ZonedDateTime): Boolean =
        campaignJpaRepository.increaseIssuedCountWithinLimit(id, now) == 1
}
