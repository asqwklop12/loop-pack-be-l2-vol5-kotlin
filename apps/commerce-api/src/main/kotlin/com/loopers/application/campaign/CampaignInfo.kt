package com.loopers.application.campaign

import com.loopers.domain.campaign.Campaign
import java.time.ZonedDateTime

data class CampaignInfo(
    val id: Long,
    val name: String,
    val issuable: Boolean,
    val totalLimit: Int,
    val issuedCount: Int,
    val startDate: ZonedDateTime,
    val endDate: ZonedDateTime,
) {
    companion object {
        fun from(campaign: Campaign): CampaignInfo {
            return CampaignInfo(
                id = campaign.id,
                name = campaign.name,
                issuable = campaign.issuable,
                totalLimit = campaign.totalLimit,
                issuedCount = campaign.issuedCount,
                startDate = campaign.startDate,
                endDate = campaign.endDate,
            )
        }
    }
}
