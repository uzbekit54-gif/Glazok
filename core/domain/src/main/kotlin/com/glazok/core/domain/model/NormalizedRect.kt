package com.glazok.core.domain.model

import kotlin.math.sqrt

/**
 * Прямоугольная область в относительных нормализованных координатах [0.0f .. 1.0f].
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    fun distanceTo(other: NormalizedRect): Float {
        val dx = centerX - other.centerX
        val dy = centerY - other.centerY
        return sqrt(dx * dx + dy * dy)
    }

    fun isVerticallyClose(other: NormalizedRect, maxVerticalGap: Float = 0.08f): Boolean {
        val verticalGap = if (bottom < other.top) other.top - bottom else if (other.bottom < top) top - other.bottom else 0f
        return verticalGap <= maxVerticalGap
    }

    fun overlapsHorizontally(other: NormalizedRect): Boolean {
        return left < other.right && right > other.left
    }
}
