package com.loopers.interfaces.api.admin

import com.loopers.application.campaign.CampaignFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/campaigns")
class CampaignV1AdminController(
    private val campaignFacade: CampaignFacade,
) {
    @PostMapping
    fun createCampaign(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @RequestBody request: AdminV1Dto.CampaignV1.CreateRequest,
    ): ApiResponse<AdminV1Dto.CampaignV1.Response> {
        AdminRole.guard(role)

        return campaignFacade.createCampaign(request.name, request.totalLimit, request.startDate, request.endDate)
            .let { AdminV1Dto.CampaignV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }
}
