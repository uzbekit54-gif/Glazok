package com.example.glazok.vision

import com.glazok.core.domain.model.PriceTag

/**
 * Реактивное состояние анализа текущего кадра камеры.
 */
data class VisionFrameState(
    val detectedItems: List<DetectedItem> = emptyList(),
    val detectedPriceTags: List<PriceTag> = emptyList(),
    val primaryTarget: PriceTag? = null,
    val primaryItem: DetectedItem? = null,
    val inferenceTimeMs: Long = 0L,
    val isTorchOn: Boolean = false
)
