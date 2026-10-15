package com.glazok.core.domain.model

/**
 * Распознанный и структурированный ценник.
 */
data class PriceTag(
    val finalPrice: Double,
    val originalPrice: Double? = null,
    val isLoyaltyPrice: Boolean = false,
    val unitOfMeasure: String? = null,
    val currency: String = "RUB",
    val boundingBox: NormalizedRect,
    val rawText: String
) {
    /**
     * Формирует речевую фразу с акцентом на выгоду согласно ADR-005.
     */
    fun toSpeechUtterance(): String {
        val formattedFinal = formatPrice(finalPrice)
        val unitSuffix = if (!unitOfMeasure.isNullOrBlank()) " за $unitOfMeasure" else ""

        return if (isLoyaltyPrice && originalPrice != null && originalPrice > finalPrice) {
            val formattedOriginal = formatPrice(originalPrice)
            "$formattedFinal рублей по карте, обычная $formattedOriginal$unitSuffix"
        } else if (originalPrice != null && originalPrice > finalPrice) {
            val formattedOriginal = formatPrice(originalPrice)
            "$formattedFinal рублей со скидкой, без скидки $formattedOriginal$unitSuffix"
        } else {
            "$formattedFinal рублей$unitSuffix"
        }
    }

    companion object {
        fun formatPrice(price: Double): String {
            return if (price % 1.0 == 0.0) {
                price.toLong().toString()
            } else {
                val rubles = price.toLong()
                val kopecks = Math.round((price - rubles) * 100)
                if (kopecks == 0L) {
                    rubles.toString()
                } else {
                    "$rubles руб $kopecks коп"
                }
            }
        }
    }
}
