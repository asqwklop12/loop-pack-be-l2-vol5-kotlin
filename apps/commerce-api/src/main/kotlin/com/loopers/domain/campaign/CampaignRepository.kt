package com.loopers.domain.campaign

interface CampaignRepository {
    fun save(campaign: Campaign): Campaign
}
