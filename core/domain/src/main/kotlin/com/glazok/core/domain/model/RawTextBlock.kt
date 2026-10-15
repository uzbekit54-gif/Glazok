package com.glazok.core.domain.model

/**
 * Сырой блок текста, полученный из OCR (например, ML Kit Text Recognition).
 */
data class RawTextBlock(
    val text: String,
    val boundingBox: NormalizedRect,
    val confidence: Float = 1.0f
)
