package com.amp.antigem.data.provider

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ApiProtocol { OPENAI_COMPAT, ANTHROPIC_COMPAT }

@Serializable
data class CustomProviderConfig(
    val id: String,
    val name: String,
    val apiBaseUrl: String,
    val protocol: ApiProtocol = ApiProtocol.OPENAI_COMPAT,
    val iconUrl: String? = null,
    val customModels: List<String> = emptyList(),
    val defaultModel: String? = null
)

object CustomProviderStore {
    private const val PREFS_NAME = "custom_providers_prefs"
    private const val KEY_PROVIDERS = "providers_json"

    fun saveAll(context: Context, providers: List<CustomProviderConfig>) {
        val json = Json.encodeToString(providers)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROVIDERS, json).apply()
    }

    fun loadAll(context: Context): List<CustomProviderConfig> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROVIDERS, null) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyList() }
    }

    fun saveApiKey(context: Context, providerId: String, apiKey: String) {
        context.getSharedPreferences("custom_provider_keys", Context.MODE_PRIVATE)
            .edit().putString("key_$providerId", apiKey).apply()
    }

    fun getApiKey(context: Context, providerId: String): String? {
        return context.getSharedPreferences("custom_provider_keys", Context.MODE_PRIVATE)
            .getString("key_$providerId", null)
    }

    fun deleteApiKey(context: Context, providerId: String) {
        context.getSharedPreferences("custom_provider_keys", Context.MODE_PRIVATE)
            .edit().remove("key_$providerId").apply()
    }
}
