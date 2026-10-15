package com.example.glazok.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glazok.vision.VisionFrameState

/**
 * Высокопроизводительный Canvas-оверлей поверх видоискателя камеры.
 * Отрисовывает:
 * - Рамки распознанных предметов (синий цвет)
 * - Рамки ценников с бейджами стоимости (зеленый цвет)
 * - Центральное перекрестье (Reticle), меняющее цвет на золотой при захвате цели
 */
@Composable
fun GlazokHudOverlay(
    frameState: VisionFrameState,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Отрисовка рамок предметов (синий цвет + бейдж названия)
        for (item in frameState.detectedItems) {
            val left = item.boundingBox.left * canvasWidth
            val top = item.boundingBox.top * canvasHeight
            val width = item.boundingBox.width * canvasWidth
            val height = item.boundingBox.height * canvasHeight

            drawRoundRect(
                color = Color(0xFF2979FF).copy(alpha = 0.85f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )

            // Бейдж с названием предмета над рамкой
            val itemLabelResult = textMeasurer.measure(
                text = item.label,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            val badgeWidth = itemLabelResult.size.width + 16.dp.toPx()
            val badgeHeight = itemLabelResult.size.height + 6.dp.toPx()
            val badgeTop = (top - badgeHeight - 4.dp.toPx()).coerceAtLeast(0f)

            drawRoundRect(
                color = Color(0xFF2979FF),
                topLeft = Offset(left, badgeTop),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            drawText(
                textLayoutResult = itemLabelResult,
                topLeft = Offset(left + 8.dp.toPx(), badgeTop + 3.dp.toPx())
            )
        }

        // 2. Отрисовка ценников (зеленый цвет + плашка с ценой)
        for (tag in frameState.detectedPriceTags) {
            val left = tag.boundingBox.left * canvasWidth
            val top = tag.boundingBox.top * canvasHeight
            val width = tag.boundingBox.width * canvasWidth
            val height = tag.boundingBox.height * canvasHeight

            val isPrimary = tag == frameState.primaryTarget
            val boxColor = if (isPrimary) Color(0xFFFFD700) else Color(0xFF00E676)
            val strokeWidth = if (isPrimary) 4.dp.toPx() else 2.5.dp.toPx()

            // Рамка ценника
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )

            // Бейдж с ценой над рамкой
            val priceBadgeText = if (tag.isLoyaltyPrice && tag.originalPrice != null) {
                "${tag.finalPrice.toInt()} ₽ (карта)"
            } else {
                "${tag.finalPrice.toInt()} ₽"
            }

            val textLayoutResult = textMeasurer.measure(
                text = priceBadgeText,
                style = TextStyle(
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            val badgeWidth = textLayoutResult.size.width + 20.dp.toPx()
            val badgeHeight = textLayoutResult.size.height + 8.dp.toPx()
            val badgeTop = (top - badgeHeight - 4.dp.toPx()).coerceAtLeast(0f)

            // Фон бейджа
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, badgeTop),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            // Текст цены
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(left + 10.dp.toPx(), badgeTop + 4.dp.toPx())
            )
        }

        // 3. Центральное перекрестье (Reticle)
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        val reticleRadius = 40.dp.toPx()
        val hasTarget = frameState.primaryTarget != null || frameState.primaryItem != null

        val reticleColor = if (hasTarget) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f)
        val reticleStroke = if (hasTarget) 3.5.dp.toPx() else 2.dp.toPx()

        // Центральный круг
        drawCircle(
            color = reticleColor,
            radius = reticleRadius,
            center = Offset(centerX, centerY),
            style = Stroke(
                width = reticleStroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
            )
        )

        // Центральная точка
        drawCircle(
            color = reticleColor,
            radius = if (hasTarget) 5.dp.toPx() else 3.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}
