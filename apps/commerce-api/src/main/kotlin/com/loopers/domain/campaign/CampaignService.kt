package com.loopers.domain.campaign

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class CampaignService(
    private val campaignRepository: CampaignRepository,
) {
    @Transactional
    fun create(name: String, totalLimit: Int, startDate: ZonedDateTime, endDate: ZonedDateTime): Campaign {
        return campaignRepository.save(
            Campaign(name = name, totalLimit = totalLimit, startDate = startDate, endDate = endDate),
        )
    }
}
