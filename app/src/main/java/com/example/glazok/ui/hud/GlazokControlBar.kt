package com.example.glazok.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glazok.vision.VisionFrameState

/**
 * Панель управления и статусная плашка видоискателя «Глазок».
 */
@Composable
fun GlazokControlBar(
    frameState: VisionFrameState,
    isTorchOn: Boolean,
    currentZoom: Float,
    isVlmLoading: Boolean,
    onToggleTorch: () -> Unit,
    onSetZoom: (Float) -> Unit,
    onSpeakCurrentTarget: () -> Unit,
    onTriggerVlm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Верхняя информационная плашка: статус захвата и время инференса
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isVlmLoading) {
                    Text(
                        text = "⏳ Читаю полный состав через VLM...",
                        color = Color(0xFFFF9100),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else if (frameState.primaryItem != null) {
                    val item = frameState.primaryItem
                    val labelText = if (item.associatedPrice != null) {
                        "${item.label} • ${item.associatedPrice.finalPrice.toInt()} ₽"
                    } else {
                        item.label
                    }
                    Text(
                        text = "В прицеле: $labelText",
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else if (frameState.primaryTarget != null) {
                    val target = frameState.primaryTarget
                    Text(
                        text = "Захвачен: ${target.toSpeechUtterance()}",
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "Наведите прицел на предмет или ценник",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }

                // Индикатор задержки
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${frameState.inferenceTimeMs} мс",
                        fontSize = 11.sp,
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Нижняя панель быстрых действий (Зум, Фонарик, Кнопка VLM-состава, Кнопка озвучки)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.75f),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Переключатели зума 1x, 2x, 3x
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.0f to "1x", 0.5f to "2x", 1.0f to "3x").forEach { (zoomValue, label) ->
                        val isSelected = (currentZoom - zoomValue) in -0.1f..0.1f
                        FilledTonalButton(
                            onClick = { onSetZoom(zoomValue) },
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSelected) Color(0xFF2979FF) else Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Кнопка фонарика (Torch)
                IconButton(
                    onClick = onToggleTorch,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isTorchOn) Color(0xFFFFD700) else Color.White.copy(alpha = 0.15f),
                        contentColor = if (isTorchOn) Color.Black else Color.White
                    )
                ) {
                    Text(
                        text = if (isTorchOn) "💡" else "🔦",
                        fontSize = 18.sp
                    )
                }

                // Кнопка Контура 2: Глубокий VLM-анализ состава
                ElevatedButton(
                    onClick = onTriggerVlm,
                    enabled = !isVlmLoading,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFFFF9100),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = if (isVlmLoading) "⏳" else "🔍 Состав", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Кнопка принудительной повторной озвучки
                ElevatedButton(
                    onClick = onSpeakCurrentTarget,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "🔊", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
