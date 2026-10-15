package com.example.glazok.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.glazok.vision.DetectedItem
import com.glazok.core.domain.model.PriceTag
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class SpeechPriority {
    CRITICAL,    // Прерывает текущую речь (QUEUE_FLUSH)
    TARGET_LOCK, // Озвучивание захваченного ценника (QUEUE_ADD с проверкой дебаунса)
    BACKGROUND   // Фоновые подсказки (QUEUE_ADD)
}

/**
 * Арбитр очереди речи с защитой от "болтливости" (Speech Fatigue Guard).
 * Управляет Android TextToSpeech, подавляет повторы одного ценника/предмета в течение 5 секунд,
 * выстраивает приоритеты сообщений и работает в связке с тактильным откликом.
 */
class SpeechQueueArbiter(
    context: Context,
    private val hapticManager: HapticManager? = null,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private val isReady = AtomicBoolean(false)

    // Кэш времени последних озвученных фраз: Хеш/Текст -> Время в мс (TTL 5000ms)
    private val spokenHistory = ConcurrentHashMap<String, Long>()
    private val debounceTtlMs = 5000L

    init {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {}
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("ru-RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Если русский недоступен в TTS, используем дефолтный язык устройства
                tts?.setLanguage(Locale.getDefault())
            }
            // Слегка ускоренный темп (1.15x) для оперативного ассистивного отклика
            tts?.setSpeechRate(1.15f)
            tts?.setPitch(1.0f)
            isReady.set(true)
            onInitComplete(true)
        } else {
            isReady.set(false)
            onInitComplete(false)
        }
    }

    /**
     * Озвучивает распознанный предмет по центру (и привязанный ценник при наличии).
     */
    fun announceItem(item: DetectedItem, priority: SpeechPriority = SpeechPriority.TARGET_LOCK) {
        val speechText = if (item.associatedPrice != null) {
            "${item.label}. Цена: ${item.associatedPrice.toSpeechUtterance()}"
        } else {
            item.label
        }

        val cacheKey = "ITEM_${item.trackingId ?: item.label}_${item.associatedPrice?.finalPrice}"
        val currentTime = System.currentTimeMillis()
        val lastSpokenTime = spokenHistory[cacheKey] ?: 0L

        if (currentTime - lastSpokenTime < debounceTtlMs && priority != SpeechPriority.CRITICAL) {
            return
        }

        spokenHistory[cacheKey] = currentTime
        cleanExpiredCache(currentTime)

        hapticManager?.vibrateTargetLock()
        speak(speechText, priority, utteranceId = cacheKey)
    }

    /**
     * Озвучивает распознанный ценник с проверкой дебаунса и тактильным откликом.
     */
    fun announcePriceTag(priceTag: PriceTag, priority: SpeechPriority = SpeechPriority.TARGET_LOCK) {
        val speechText = priceTag.toSpeechUtterance()
        val cacheKey = "PRICE_${priceTag.finalPrice}_${priceTag.originalPrice}_${priceTag.unitOfMeasure}"

        val currentTime = System.currentTimeMillis()
        val lastSpokenTime = spokenHistory[cacheKey] ?: 0L

        // Если этот же ценник уже звучал менее 5 секунд назад — пропускаем
        if (currentTime - lastSpokenTime < debounceTtlMs && priority != SpeechPriority.CRITICAL) {
            return
        }

        spokenHistory[cacheKey] = currentTime
        cleanExpiredCache(currentTime)

        // Тактильный щелчок при озвучке нового зафиксированного ценника
        hapticManager?.vibrateTargetLock()

        speak(speechText, priority, utteranceId = cacheKey)
    }

    /**
     * Прямая отправка фразы в движок синтеза речи с учетом приоритета.
     */
    fun speak(text: String, priority: SpeechPriority = SpeechPriority.BACKGROUND, utteranceId: String = System.currentTimeMillis().toString()) {
        if (!isReady.get() || text.isBlank()) return

        val queueMode = when (priority) {
            SpeechPriority.CRITICAL -> TextToSpeech.QUEUE_FLUSH
            SpeechPriority.TARGET_LOCK -> TextToSpeech.QUEUE_FLUSH // Быстрый переход к новому ценнику
            SpeechPriority.BACKGROUND -> TextToSpeech.QUEUE_ADD
        }

        tts?.speak(text, queueMode, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady.set(false)
    }

    private fun cleanExpiredCache(currentTime: Long) {
        val iterator = spokenHistory.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value > debounceTtlMs * 2) {
                iterator.remove()
            }
        }
    }
}
