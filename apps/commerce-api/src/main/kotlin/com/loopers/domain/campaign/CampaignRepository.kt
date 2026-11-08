package com.loopers.domain.campaign

import java.time.ZonedDateTime

interface CampaignRepository {
    fun save(campaign: Campaign): Campaign
    fun find(id: Long): Campaign?
    fun increaseIssuedCountWithinLimit(id: Long, now: ZonedDateTime): Boolean
}
