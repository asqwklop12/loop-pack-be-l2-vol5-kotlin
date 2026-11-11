package com.loopers.application.coupon

import com.loopers.domain.campaign.Campaign
import com.loopers.domain.campaign.CampaignIssueCache
import com.loopers.domain.campaign.CampaignService
import com.loopers.domain.coupon.CouponIssuePermits
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.IssuedCouponCache
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CouponFacade(
    private val campaignService: CampaignService,
    private val couponService: CouponService,
    private val couponIssuer: CouponIssuer,
    private val campaignIssueCache: CampaignIssueCache,
    private val issuedCouponCache: IssuedCouponCache,
    private val couponIssuePermits: CouponIssuePermits,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun issue(campaignId: Long, userId: Long): CouponIssueResult {
        skipIfRedisDown("기존 쿠폰 확인") { issuedCouponCache.findCouponId(campaignId, userId) }
            ?.let { return existing(CouponInfo(id = it, campaignId = campaignId, userId = userId)) }

        if (skipIfRedisDown("품절 확인") { campaignIssueCache.isSoldOut(campaignId) } == true) throw Campaign.soldOut()

        checkIssuance(campaignId)

        val token = acquirePermit()
        try {
            return issueInTransaction(campaignId, userId)
        } finally {
            releasePermit(token)
        }
    }

    fun getMyCoupons(userId: Long): List<CouponInfo> = couponService.getAllByUserId(userId).map { CouponInfo.from(it) }

    private fun checkIssuance(campaignId: Long) {
        val cached = try {
            campaignIssueCache.findIssuance(campaignId)
        } catch (e: DataAccessException) {
            log.warn("캠페인 기간 확인을 건너뛴다. campaignId={}, cause={}", campaignId, e.message)
            return
        }
        val issuance = cached ?: campaignService.getIssuance(campaignId).also {
            skipIfRedisDown("캠페인 기간 저장") { campaignIssueCache.saveIssuance(campaignId, it) }
        }
        issuance.check(ZonedDateTime.now())
    }

    private fun acquirePermit(): String {
        val token = try {
            couponIssuePermits.tryAcquire()
        } catch (e: DataAccessException) {
            log.warn("발급 자리를 확인할 수 없다. cause={}", e.message)
            throw CoreException(ErrorType.SERVICE_UNAVAILABLE, "지금은 쿠폰 발급 요청을 받을 수 있는지 확인할 수 없습니다.")
        }
        return token ?: throw CoreException(ErrorType.TOO_MANY_REQUESTS, "쿠폰 발급 요청이 많아 지금은 처리할 수 없습니다.")
    }

    private fun issueInTransaction(campaignId: Long, userId: Long): CouponIssueResult {
        val coupon = try {
            couponIssuer.issueUnlessSoldOut(campaignId, userId)
        } catch (e: DataIntegrityViolationException) {
            val coupon = couponService.find(campaignId, userId) ?: throw e
            return existing(CouponInfo.from(coupon))
        }

        if (coupon == null) {
            skipIfRedisDown("품절 표시") { campaignIssueCache.markSoldOut(campaignId) }
            throw Campaign.soldOut()
        }

        skipIfRedisDown("발급 기록") { issuedCouponCache.save(campaignId, userId, coupon.id) }
        return CouponIssueResult(coupon = CouponInfo.from(coupon), newlyIssued = true)
    }

    private fun releasePermit(token: String) {
        try {
            couponIssuePermits.release(token)
        } catch (e: DataAccessException) {
            log.warn("발급 자리를 반납하지 못했다. 자리가 하나 줄어든다. token={}, cause={}", token, e.message)
        }
    }

    private fun <T> skipIfRedisDown(step: String, block: () -> T): T? =
        try {
            block()
        } catch (e: DataAccessException) {
            log.warn("{}을 건너뛴다. cause={}", step, e.message)
            null
        }

    private fun existing(coupon: CouponInfo) = CouponIssueResult(coupon = coupon, newlyIssued = false)
}
