package com.loopers.domain.campaign

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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

    fun issue(id: Long) {
        val campaign = campaignRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 캠페인을 찾을 수 없습니다.")

        campaign.issue(ZonedDateTime.now())
    }
}
