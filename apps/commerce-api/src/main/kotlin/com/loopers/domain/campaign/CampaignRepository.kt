package com.loopers.domain.campaign

interface CampaignRepository {
    fun save(campaign: Campaign): Campaign
    fun find(id: Long): Campaign?
    fun findForUpdate(id: Long): Campaign?
}
