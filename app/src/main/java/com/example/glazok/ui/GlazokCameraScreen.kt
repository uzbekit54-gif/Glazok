package com.example.glazok.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.glazok.audio.HapticManager
import com.example.glazok.audio.SpeechPriority
import com.example.glazok.audio.SpeechQueueArbiter
import com.example.glazok.ui.hud.GlazokControlBar
import com.example.glazok.ui.hud.GlazokHudOverlay
import com.example.glazok.vision.CameraManager
import com.example.glazok.vlm.GeminiVlmService
import kotlinx.coroutines.launch

/**
 * Основной экран видоискателя «Глазок».
 * Объединяет:
 * - Контур 1 (Real-time On-device): CameraX, ML Kit, SmartPriceParser, TTS с 5с дебаунсом.
 * - Контур 2 (Deep VLM On-Demand): Двойной тап или кнопка «🔍 Состав» для подробного чтения состава и аллергенов.
 */
@Composable
fun GlazokCameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val hapticManager = remember { HapticManager(context) }
    val speechArbiter = remember { SpeechQueueArbiter(context, hapticManager) }
    val cameraManager = remember { CameraManager(context, lifecycleOwner) }
    val vlmService = remember { GeminiVlmService() }

    val frameState by cameraManager.analyzer.frameState.collectAsState()

    var isTorchOn by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableFloatStateOf(0.0f) }
    var isVlmLoading by remember { mutableStateOf(false) }
    var lastVlmResult by remember { mutableStateOf<String?>(null) }

    // Функция запуска глубокого анализа Контура 2
    val triggerVlmAnalysis = {
        if (!isVlmLoading) {
            isVlmLoading = true
            hapticManager.vibrateVlmTriggered()
            speechArbiter.speak("Анализирую состав товара...", SpeechPriority.CRITICAL)

            cameraManager.takeSnapshot(
                onSuccess = { bitmap ->
                    scope.launch {
                        val result = vlmService.analyzeProduct(bitmap)
                        lastVlmResult = result
                        isVlmLoading = false
                        speechArbiter.speak(result, SpeechPriority.CRITICAL)
                    }
                },
                onError = { exc ->
                    isVlmLoading = false
                    speechArbiter.speak("Не удалось сделать снимок: ${exc.message}", SpeechPriority.CRITICAL)
                }
            )
        }
    }

    // Автоматическая озвучка захваченного ценника (Контур 1, арбитр сам проверяет 5с дебаунс)
    LaunchedEffect(frameState.primaryTarget) {
        val target = frameState.primaryTarget
        if (target != null && !isVlmLoading) {
            speechArbiter.announcePriceTag(target, SpeechPriority.TARGET_LOCK)
        }
    }

    // Корректное освобождение ресурсов при закрытии экрана
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            speechArbiter.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        triggerVlmAnalysis()
                    }
                )
            }
    ) {
        // 1. Полноэкранный видоискатель CameraX
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraManager.startCamera(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Высокопроизводительный Canvas HUD с рамками и прицелом
        GlazokHudOverlay(
            frameState = frameState,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Карточка с результатами глубокого VLM-анализа (Контур 2)
        lastVlmResult?.let { text ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .align(Alignment.TopCenter)
                    .clickable { lastVlmResult = null }
            ) {
                Text(
                    text = "🔍 Описание товара:\n$text\n\n(Нажмите, чтобы скрыть)",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // 4. Плавающая панель управления и статуса
        GlazokControlBar(
            frameState = frameState,
            isTorchOn = isTorchOn,
            currentZoom = currentZoom,
            isVlmLoading = isVlmLoading,
            onToggleTorch = {
                isTorchOn = cameraManager.toggleTorch()
            },
            onSetZoom = { zoomRatio ->
                currentZoom = zoomRatio
                cameraManager.setZoom(zoomRatio)
            },
            onSpeakCurrentTarget = {
                frameState.primaryTarget?.let {
                    speechArbiter.announcePriceTag(it, SpeechPriority.CRITICAL)
                } ?: run {
                    speechArbiter.speak("В центре кадра ценник не обнаружен", SpeechPriority.CRITICAL)
                }
            },
            onTriggerVlm = {
                triggerVlmAnalysis()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
