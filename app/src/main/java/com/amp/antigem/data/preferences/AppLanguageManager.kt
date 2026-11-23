package com.amp.antigem.data.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANG = "selected_language"

    const val LANG_SYSTEM = ""
    const val LANG_AR = "ar"
    const val LANG_EN = "en"

    private val _currentLanguage = MutableStateFlow(LANG_SYSTEM)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _isRtl = MutableStateFlow(false)
    val isRtl: StateFlow<Boolean> = _isRtl.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANG, LANG_SYSTEM) ?: LANG_SYSTEM
        _currentLanguage.value = lang
        applyLanguage(context, lang)
    }

    fun setLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()
        _currentLanguage.value = langCode
        applyLanguage(context, langCode)
    }

    private fun applyLanguage(context: Context, langCode: String) {
        val locale = when (langCode) {
            LANG_AR -> Locale("ar")
            LANG_EN -> Locale("en")
            else -> Locale.getDefault()
        }
        Locale.setDefault(locale)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        _isRtl.value = (locale.language == "ar")
    }

    fun layoutDirection(): LayoutDirection = if (_isRtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr
}
