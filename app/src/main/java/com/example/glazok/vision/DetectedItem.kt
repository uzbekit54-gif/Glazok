package com.example.glazok.vision

import com.glazok.core.domain.model.NormalizedRect
import com.glazok.core.domain.model.PriceTag

/**
 * Объект, обнаруженный детектором объектов (ML Kit Object Detection).
 */
data class DetectedItem(
    val trackingId: Int?,
    val label: String,
    val confidence: Float,
    val boundingBox: NormalizedRect,
    val associatedPrice: PriceTag? = null
)
