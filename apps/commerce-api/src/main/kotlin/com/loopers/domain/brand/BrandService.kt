package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun create(name: String): Brand {
        brandRepository.findByName(name)
            ?.let { throw CoreException(ErrorType.CONFLICT, "[name = $name] 이미 존재하는 브랜드입니다.") }

        return brandRepository.save(Brand(name = name))
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Brand {
        return brandRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 브랜드를 찾을 수 없습니다.")
    }
}
