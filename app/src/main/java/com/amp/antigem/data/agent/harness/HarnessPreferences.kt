package com.amp.antigem.data.agent.harness

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** DeepSeek Harness choices the app remembers: model, profile, reasoning effort, and approvals mode. */
class HarnessPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("harness_prefs", Context.MODE_PRIVATE)

    private val _model = MutableStateFlow(prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty())
    val model: StateFlow<String> = _model.asStateFlow()

    private val _profile = MutableStateFlow(prefs.getString(KEY_PROFILE, DEFAULT_PROFILE).orEmpty())
    val profile: StateFlow<String> = _profile.asStateFlow()

    private val _effort = MutableStateFlow(prefs.getString(KEY_EFFORT, "medium").orEmpty())
    val effort: StateFlow<String> = _effort.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
        _model.value = model
    }

    fun setProfile(profile: String) {
        prefs.edit().putString(KEY_PROFILE, profile).apply()
        _profile.value = profile
    }

    fun setEffort(effort: String) {
        prefs.edit().putString(KEY_EFFORT, effort).apply()
        _effort.value = effort
    }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
        _apiKey.value = key
    }

    companion object {
        private const val KEY_MODEL = "harness_model"
        private const val KEY_PROFILE = "harness_profile"
        private const val KEY_EFFORT = "harness_effort"
        private const val KEY_API_KEY = "harness_api_key"

        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_PROFILE = "acp"

        val AVAILABLE_MODELS = listOf(
            "deepseek-chat" to "DeepSeek V3 (Chat & Coding)",
            "deepseek-reasoner" to "DeepSeek R1 (Deep Thinking & Reasoning)",
            "deepseek-coder" to "DeepSeek Coder"
        )
    }
}
