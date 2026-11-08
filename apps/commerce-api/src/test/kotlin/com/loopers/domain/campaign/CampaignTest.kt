package com.loopers.domain.campaign

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class CampaignTest {
    private val now = ZonedDateTime.now()

    private fun campaign(
        totalLimit: Int = 100,
        startDate: ZonedDateTime = now.plusDays(1),
        endDate: ZonedDateTime = now.plusDays(8),
    ) = Campaign(name = "신규가입 쿠폰", totalLimit = totalLimit, startDate = startDate, endDate = endDate)

    @DisplayName("캠페인을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 값이면, 발급 가능 상태로 발급 수 0에서 시작한다.")
        @Test
        fun createsIssuableCampaignWithZeroIssued_whenValid() {
            val campaign = campaign()

            assertAll(
                { assertThat(campaign.name).isEqualTo("신규가입 쿠폰") },
                { assertThat(campaign.issuable).isTrue() },
                { assertThat(campaign.totalLimit).isEqualTo(100) },
                { assertThat(campaign.issuedCount).isZero() },
            )
        }

        @DisplayName("이미 시작했어도 끝나지 않았으면, 정상적으로 생성된다.")
        @Test
        fun createsCampaign_whenStartIsPastButEndIsFuture() {
            val campaign = campaign(startDate = now.minusDays(1))

            assertThat(campaign.issuable).isTrue()
        }

        @DisplayName("총 발급 한도가 1이면, 정상적으로 생성된다.")
        @Test
        fun createsCampaign_whenTotalLimitIsOne() {
            assertThat(campaign(totalLimit = 1).totalLimit).isEqualTo(1)
        }

        @DisplayName("총 발급 한도가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenTotalLimitIsZero() {
            val result = assertThrows<CoreException> { campaign(totalLimit = 0) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("총 발급 한도가 음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenTotalLimitIsNegative() {
            val result = assertThrows<CoreException> { campaign(totalLimit = -1) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("시작과 끝이 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenStartEqualsEnd() {
            val at = now.plusDays(1)

            val result = assertThrows<CoreException> { campaign(startDate = at, endDate = at) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("시작이 끝보다 늦으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenStartIsAfterEnd() {
            val result = assertThrows<CoreException> {
                campaign(startDate = now.plusDays(8), endDate = now.plusDays(1))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("끝이 이미 지났으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEndIsPast() {
            val result = assertThrows<CoreException> {
                campaign(startDate = now.minusDays(8), endDate = now.minusDays(1))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    inner class Issue {
        @DisplayName("기간 안이고 수량이 남았으면, 발급 수가 1 늘어난다.")
        @Test
        fun increasesIssuedCount_whenIssuable() {
            val campaign = campaign(startDate = now.minusDays(1))

            campaign.issue(now)

            assertThat(campaign.issuedCount).isEqualTo(1)
        }

        @DisplayName("한도만큼 발급됐으면, BAD_REQUEST 예외가 발생하고 발급 수가 그대로다.")
        @Test
        fun throwsBadRequest_whenSoldOut() {
            val campaign = campaign(totalLimit = 1, startDate = now.minusDays(1)).also { it.issue(now) }

            val result = assertThrows<CoreException> { campaign.issue(now) }

            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                { assertThat(campaign.issuedCount).isEqualTo(1) },
            )
        }

        @DisplayName("발급 기간이 지났으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPeriodEnded() {
            val campaign = campaign(startDate = now.minusDays(1), endDate = now.plusDays(1))

            val result = assertThrows<CoreException> { campaign.issue(now.plusDays(2)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("발급 기간이 아직 시작 전이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPeriodNotStarted() {
            val campaign = campaign(startDate = now.plusDays(1))

            val result = assertThrows<CoreException> { campaign.issue(now) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
