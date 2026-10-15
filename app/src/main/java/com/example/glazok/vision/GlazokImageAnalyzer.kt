package com.example.glazok.vision

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.glazok.core.domain.model.NormalizedRect
import com.glazok.core.domain.model.PriceTag
import com.glazok.core.domain.model.RawTextBlock
import com.glazok.core.domain.parser.SmartPriceParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Высокопроизводительный анализатор видеопотока CameraX (Контур 1).
 * Выполняет параллельную детекцию объектов и текста через Google ML Kit,
 * нормализует координаты и передает блоки в SmartPriceParser.
 */
class GlazokImageAnalyzer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val parser: SmartPriceParser = SmartPriceParser()
) : ImageAnalysis.Analyzer {

    private val isBusy = AtomicBoolean(false)

    private val _frameState = MutableStateFlow(VisionFrameState())
    val frameState: StateFlow<VisionFrameState> = _frameState.asStateFlow()

    // Режим потокового отслеживания объектов с trackingId
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    // Текстовый распознаватель (Latin + цифры / символы)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isBusy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val startTime = System.currentTimeMillis()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val frameWidth = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
        val frameHeight = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()

        scope.launch {
            try {
                // 1. Параллельный запуск детекции объектов и распознавания текста
                val objectsDeferred = objectDetector.process(inputImage)
                val textDeferred = textRecognizer.process(inputImage)

                val detectedMlObjects = objectsDeferred.await()
                val detectedMlText = textDeferred.await()

                // 2. Преобразование текстовых блоков в доменные модели
                val rawTextBlocks = detectedMlText.textBlocks.mapNotNull { block ->
                    val rect = block.boundingBox ?: return@mapNotNull null
                    val normRect = NormalizedRect(
                        left = (rect.left.toFloat() / frameWidth).coerceIn(0f, 1f),
                        top = (rect.top.toFloat() / frameHeight).coerceIn(0f, 1f),
                        right = (rect.right.toFloat() / frameWidth).coerceIn(0f, 1f),
                        bottom = (rect.bottom.toFloat() / frameHeight).coerceIn(0f, 1f)
                    )
                    RawTextBlock(
                        text = block.text,
                        boundingBox = normRect
                    )
                }

                // 3. Интеллектуальный парсинг ценников
                val parsedPriceTags = parser.parse(rawTextBlocks)

                // 4. Преобразование объектов ML Kit и перевод меток на русский язык
                val items = detectedMlObjects.map { mlObj ->
                    val rect = mlObj.boundingBox
                    val normRect = NormalizedRect(
                        left = (rect.left.toFloat() / frameWidth).coerceIn(0f, 1f),
                        top = (rect.top.toFloat() / frameHeight).coerceIn(0f, 1f),
                        right = (rect.right.toFloat() / frameWidth).coerceIn(0f, 1f),
                        bottom = (rect.bottom.toFloat() / frameHeight).coerceIn(0f, 1f)
                    )

                    val rawLabel = mlObj.labels.firstOrNull()?.text ?: "Предмет"
                    val russianLabel = translateMlKitLabel(rawLabel)
                    val confidence = mlObj.labels.firstOrNull()?.confidence ?: 0.5f

                    // Поиск ближайшего ценника к объекту
                    val nearestPrice = parsedPriceTags.minByOrNull { tag ->
                        tag.boundingBox.distanceTo(normRect)
                    }

                    DetectedItem(
                        trackingId = mlObj.trackingId,
                        label = russianLabel,
                        confidence = confidence,
                        boundingBox = normRect,
                        associatedPrice = if (nearestPrice != null && nearestPrice.boundingBox.distanceTo(normRect) < 0.35f) nearestPrice else null
                    )
                }

                // 5. Строгая фильтрация по ЦЕНТРУ КАМЕРЫ: выделять ТОЛЬКО объект в прицеле
                val centeredItem = items.filter { item ->
                    val rect = item.boundingBox
                    val containsCenter = 0.5f in rect.left..rect.right && 0.5f in rect.top..rect.bottom
                    val distToCenterSq = (rect.centerX - 0.5f).let { it * it } + (rect.centerY - 0.5f).let { it * it }
                    containsCenter || distToCenterSq <= 0.22f * 0.22f
                }.minByOrNull { item ->
                    val dx = item.boundingBox.centerX - 0.5f
                    val dy = item.boundingBox.centerY - 0.5f
                    dx * dx + dy * dy
                }

                // В кадре оставляем ИСКЛЮЧИТЕЛЬНО центральный предмет (другие предметы не выделяем)
                val visibleItems = if (centeredItem != null) listOf(centeredItem) else emptyList()

                // Фильтрация ценника: только ценник по центру или относящийся к центральному предмету
                val centeredPriceTag = parsedPriceTags.filter { tag ->
                    val rect = tag.boundingBox
                    val containsCenter = 0.5f in rect.left..rect.right && 0.5f in rect.top..rect.bottom
                    val distToCenterSq = (rect.centerX - 0.5f).let { it * it } + (rect.centerY - 0.5f).let { it * it }
                    containsCenter || distToCenterSq <= 0.22f * 0.22f || (centeredItem != null && tag.boundingBox.distanceTo(centeredItem.boundingBox) < 0.30f)
                }.minByOrNull { tag ->
                    val dx = tag.boundingBox.centerX - 0.5f
                    val dy = tag.boundingBox.centerY - 0.5f
                    dx * dx + dy * dy
                }

                val visiblePriceTags = if (centeredPriceTag != null) listOf(centeredPriceTag) else emptyList()

                val elapsed = System.currentTimeMillis() - startTime

                _frameState.value = _frameState.value.copy(
                    detectedItems = visibleItems,
                    detectedPriceTags = visiblePriceTags,
                    primaryTarget = centeredPriceTag,
                    primaryItem = centeredItem,
                    inferenceTimeMs = elapsed
                )

            } catch (e: Exception) {
                // Логирование или подавление сбоев единичных кадров
            } finally {
                imageProxy.close()
                isBusy.set(false)
            }
        }
    }

    private fun translateMlKitLabel(rawLabel: String): String {
        return when (rawLabel.lowercase().trim()) {
            "food" -> "Продукт питания"
            "packaged goods", "packaged goods (food)" -> "Упаковка товара"
            "home goods" -> "Предмет быта"
            "fashion goods" -> "Одежда или обувь"
            "plant" -> "Растение"
            "place" -> "Пространство"
            else -> if (rawLabel.isNotBlank()) rawLabel else "Предмет"
        }
    }

    fun release() {
        objectDetector.close()
        textRecognizer.close()
    }
}
