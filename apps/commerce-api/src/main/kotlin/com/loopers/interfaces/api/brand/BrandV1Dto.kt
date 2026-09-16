package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandInfo

class BrandV1Dto {
    data class BrandResponse(
        val id: Long,
        val name: String,
    ) {
        companion object {
            fun from(info: BrandInfo): BrandResponse {
                return BrandResponse(
                    id = info.id,
                    name = info.name,
                )
            }
        }
    }
}
