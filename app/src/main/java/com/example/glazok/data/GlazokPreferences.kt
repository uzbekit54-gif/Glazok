package com.example.glazok.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Менеджер настроек приложения (сохранение Gemini API Key).
 */
class GlazokPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("glazok_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }

    fun getGeminiApiKey(): String {
        return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }
}
