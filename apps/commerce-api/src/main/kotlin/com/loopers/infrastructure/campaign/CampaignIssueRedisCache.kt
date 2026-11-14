package com.loopers.infrastructure.campaign

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.campaign.CampaignIssuance
import com.loopers.domain.campaign.CampaignIssueCache
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CampaignIssueRedisCache(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
) : CampaignIssueCache {
    override fun isSoldOut(campaignId: Long): Boolean = redisTemplate.hasKey(soldOutKey(campaignId))

    override fun markSoldOut(campaignId: Long) {
        redisTemplate.opsForValue().set(soldOutKey(campaignId), SOLD_OUT)
    }

    override fun findIssuance(campaignId: Long): CampaignIssuance? {
        val fields = redisTemplate.opsForHash<String, String>().entries(campaignKey(campaignId))
        if (fields.isEmpty()) return null
        return CampaignIssuance(
            issuable = fields.getValue(ISSUABLE).toBoolean(),
            startDate = ZonedDateTime.parse(fields.getValue(START_DATE)),
            endDate = ZonedDateTime.parse(fields.getValue(END_DATE)),
        )
    }

    override fun saveIssuance(campaignId: Long, issuance: CampaignIssuance) {
        redisTemplate.opsForHash<String, String>().putAll(
            campaignKey(campaignId),
            mapOf(
                ISSUABLE to issuance.issuable.toString(),
                START_DATE to issuance.startDate.toString(),
                END_DATE to issuance.endDate.toString(),
            ),
        )
    }

    companion object {
        private const val SOLD_OUT = "1"
        private const val ISSUABLE = "issuable"
        private const val START_DATE = "startDate"
        private const val END_DATE = "endDate"

        fun campaignKey(campaignId: Long) = "campaign:$campaignId"

        fun soldOutKey(campaignId: Long) = "campaign:$campaignId:soldout"
    }
}
