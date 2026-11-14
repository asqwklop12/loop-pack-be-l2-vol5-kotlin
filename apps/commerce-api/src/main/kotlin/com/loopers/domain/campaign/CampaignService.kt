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

    @Transactional(readOnly = true)
    fun getIssuance(id: Long): CampaignIssuance = getCampaign(id).issuance()

    fun issue(id: Long, count: Int): Int {
        val campaign = campaignRepository.findForUpdate(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 캠페인을 찾을 수 없습니다.")
        return campaign.issue(count, ZonedDateTime.now())
    }

    private fun getCampaign(id: Long): Campaign =
        campaignRepository.find(id) ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 캠페인을 찾을 수 없습니다.")
}
