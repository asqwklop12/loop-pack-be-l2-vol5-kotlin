package com.loopers.domain.campaign

interface CampaignIssueCache {
    fun isSoldOut(campaignId: Long): Boolean

    fun markSoldOut(campaignId: Long)

    fun findIssuance(campaignId: Long): CampaignIssuance?

    fun saveIssuance(campaignId: Long, issuance: CampaignIssuance)
}
