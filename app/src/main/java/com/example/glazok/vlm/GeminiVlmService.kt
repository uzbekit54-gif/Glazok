package com.example.glazok.vlm

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Сервис Контура 2: глубокий анализ этикеток и товаров через Gemini VLM.
 * Захватывает детали, невидимые локальным моделям: состав на русском языке, аллергены, сроки годности.
 */
class GeminiVlmService(
    private var apiKey: String = ""
) {

    fun setApiKey(key: String) {
        this.apiKey = key
    }

    suspend fun analyzeProduct(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Для чтения полного состава и аллергенов укажите ключ Gemini API в настройках."
        }

        try {
            val base64Image = encodeBitmapToBase64(bitmap)
            val requestJson = buildRequestBody(base64Image)

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                parseGeminiResponse(responseText)
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                "Не удалось распознать товар, код ошибки: $responseCode"
            }
        } catch (e: Exception) {
            "Ошибка соединения с сервисом описания товаров: ${e.localizedMessage ?: "нет сети"}"
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        // Оптимизация размера для ускорения отправки (макс 1024x1024)
        val maxDimension = 1024
        val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            ratio
        } else {
            1.0f
        }

        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun buildRequestBody(base64Image: String): String {
        val prompt = "Ты — ассистент незрячего человека. На фотографии — товар или ценник в магазине. " +
                "Кратко (2-3 предложения) на русском языке ответь: " +
                "1) Точное название и бренд товара. " +
                "2) Состав и аллергены (если видно). " +
                "3) Срок годности или дату (если видно). " +
                "Отвечай простым текстом без списков и markdown, удобно для озвучивания голосом."

        val json = JSONObject()
        val contents = JSONArray()
        val contentObj = JSONObject()
        val parts = JSONArray()

        // Текстовая часть промпта
        val textPart = JSONObject().apply {
            put("text", prompt)
        }
        parts.put(textPart)

        // Изображение
        val imagePart = JSONObject().apply {
            val inlineData = JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", base64Image)
            }
            put("inline_data", inlineData)
        }
        parts.put(imagePart)

        contentObj.put("parts", parts)
        contents.put(contentObj)
        json.put("contents", contents)

        return json.toString()
    }

    private fun parseGeminiResponse(responseText: String): String {
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates") ?: return "Товар не распознан"
        if (candidates.length() == 0) return "Товар не распознан"

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: return "Товар не распознан"
        val parts = content.optJSONArray("parts") ?: return "Товар не распознан"
        if (parts.length() == 0) return "Товар не распознан"

        val text = parts.getJSONObject(0).optString("text", "Товар не распознан")
        return text.trim()
    }
}
