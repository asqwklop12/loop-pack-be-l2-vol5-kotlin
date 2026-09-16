package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product")
class Product(
    brandId: Long,
    name: String,
    price: Money,
    stock: Stock,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false, updatable = false)
    var brandId: Long = brandId
        protected set

    var name: String = name
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "price", nullable = false))
    var price: Money = price
        protected set

    @Embedded
    @AttributeOverride(name = "quantity", column = Column(name = "stock", nullable = false))
    var stock: Stock = stock
        protected set

    init {
        guardName(name)
        guardPrice(price)
    }

    fun update(name: String, price: Money) {
        guardName(name)
        guardPrice(price)

        this.name = name
        this.price = price
    }

    fun changeStock(quantity: Int) {
        this.stock = Stock(quantity)
    }

    fun decreaseStock(quantity: Int) {
        this.stock = stock.decrease(quantity)
    }

    fun increaseStock(quantity: Int) {
        this.stock = stock.increase(quantity)
    }

    private fun guardName(name: String) {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.")
        if (name.length > MAX_NAME_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 이름은 ${MAX_NAME_LENGTH}자를 넘을 수 없습니다.")
        }
    }

    private fun guardPrice(price: Money) {
        if (price.amount < MIN_PRICE.amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 가격은 ${MIN_PRICE.amount}원 이상이어야 합니다.")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
        val MIN_PRICE = Money(1)
    }
}
