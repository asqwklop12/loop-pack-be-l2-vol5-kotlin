package com.loopers.application.campaign

import com.loopers.domain.campaign.CampaignService
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CampaignFacade(
    private val campaignService: CampaignService,
) {
    fun createCampaign(
        name: String,
        totalLimit: Int,
        startDate: ZonedDateTime,
        endDate: ZonedDateTime,
    ): CampaignInfo = CampaignInfo.from(campaignService.create(name, totalLimit, startDate, endDate))
}
