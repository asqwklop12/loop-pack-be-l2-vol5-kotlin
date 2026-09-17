package com.loopers.interfaces.api.admin

import com.loopers.application.brand.BrandInfo

class AdminV1Dto {
    class BrandV1 {
        data class CreateRequest(val name: String)

        data class UpdateRequest(val name: String)

        data class Response(val id: Long, val name: String) {
            companion object {
                fun from(info: BrandInfo) = Response(id = info.id, name = info.name)
            }
        }
    }
}
