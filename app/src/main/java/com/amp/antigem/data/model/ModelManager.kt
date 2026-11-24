package com.amp.antigem.data.model

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.amp.antigem.data.provider.ModelInfo

/**
 * Central manager that aggregates model information from all registered providers.
 * The data is exposed via LiveData so UI components can react to changes.
 */
object ModelManager {
    private val _models = MutableLiveData<List<ModelInfo>>(emptyList())
    val models: LiveData<List<ModelInfo>> = _models

    /**
     * Refresh the model list for every provider known to ProviderManager.
     * This function runs on a background dispatcher and posts results to LiveData.
     */
    fun refreshAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val allModels = mutableListOf<ModelInfo>()
            // Retrieve the list of providers from ProviderManager (must be initialized).
            val providers = com.amp.antigem.data.provider.ProviderManager.providers.value
            providers?.forEach { provider ->
                try {
                    val fetched = fetchModelsForProvider(context, provider)
                    allModels.addAll(fetched)
                } catch (e: Exception) {
                    // Log the error – in production use proper logging framework.
                    e.printStackTrace()
                }
            }
            _models.postValue(allModels)
        }
    }

    /**
     * Fetch model metadata for a single provider.
     * The implementation currently supports a few known providers.
     * For custom providers we attempt a generic OpenAI‑compatible endpoint.
     */
    private suspend fun fetchModelsForProvider(context: Context, cfg: com.amp.antigem.data.provider.ProviderConfig): List<ModelInfo> {
        return when (cfg.id) {
            "opencode" -> fetchOpenAIModels(cfg, "/v1/models")
            "codex" -> fetchOpenAIModels(cfg, "/v1/models") // Codex uses OpenAI API compatibility.
            "claude" -> fetchAnthropicModels(cfg)
            "deepseek" -> fetchDeepSeekModels(cfg)
            else -> fetchCustomProviderModels(cfg)
        }
    }

    // ---------- Helper fetchers -------------------------------------------------

    private suspend fun fetchOpenAIModels(cfg: com.amp.antigem.data.provider.ProviderConfig, endpoint: String): List<ModelInfo> {
        val apiKey = com.amp.antigem.data.provider.ProviderManager.getApiKey(cfg.id) ?: return emptyList()
        val url = URL("https://api.openai.com$endpoint")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        return parseOpenAIResponse(conn)
    }

    private suspend fun fetchAnthropicModels(cfg: com.amp.antigem.data.provider.ProviderConfig): List<ModelInfo> {
        val apiKey = com.amp.antigem.data.provider.ProviderManager.getApiKey(cfg.id) ?: return emptyList()
        val url = URL("https://api.anthropic.com/v1/models")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
        }
        return parseAnthropicResponse(conn)
    }

    private suspend fun fetchDeepSeekModels(cfg: com.amp.antigem.data.provider.ProviderConfig): List<ModelInfo> {
        // DeepSeek Harness provides a local CLI command `deepseek-harness list-models`.
        // We'll invoke it via a ProcessBuilder inside Termux.
        val harnessPath = cfg.installPath ?: return emptyList()
        val command = "$harnessPath/list-models --json"
        val proc = ProcessBuilder("bash", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        if (proc.exitValue() != 0) return emptyList()
        return parseDeepSeekJson(output, cfg.id)
    }

    private suspend fun fetchCustomProviderModels(cfg: com.amp.antigem.data.provider.ProviderConfig): List<ModelInfo> {
        // Attempt a generic OpenAI‑compatible /v1/models endpoint if baseUrl is supplied.
        val apiKey = com.amp.antigem.data.provider.ProviderManager.getApiKey(cfg.id) ?: return emptyList()
        val base = cfg.installPath ?: return emptyList() // For custom providers we reuse installPath as base URL.
        val url = URL("$base/v1/models")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        return parseOpenAIResponse(conn)
    }

    // ---------- JSON parsers ----------------------------------------------------

    private fun parseOpenAIResponse(conn: HttpURLConnection): List<ModelInfo> {
        val text = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(text)
        val data = json.getJSONArray("data")
        val list = mutableListOf<ModelInfo>()
        for (i in 0 until data.length()) {
            val obj = data.getJSONObject(i)
            val id = obj.getString("id")
            val name = id
            list.add(
                ModelInfo(
                    providerId = "openai",
                    modelId = id,
                    name = name,
                    description = null,
                    maxTokens = null,
                    capabilities = emptySet()
                )
            )
        }
        return list
    }

    private fun parseAnthropicResponse(conn: HttpURLConnection): List<ModelInfo> {
        val text = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(text)
        val data = json.getJSONArray("models")
        val list = mutableListOf<ModelInfo>()
        for (i in 0 until data.length()) {
            val obj = data.getJSONObject(i)
            val id = obj.getString("model_id")
            list.add(
                ModelInfo(
                    providerId = "anthropic",
                    modelId = id,
                    name = id,
                    description = null,
                    maxTokens = null,
                    capabilities = emptySet()
                )
            )
        }
        return list
    }

    private fun parseDeepSeekJson(jsonStr: String, providerId: String): List<ModelInfo> {
        val json = JSONObject(jsonStr)
        val models = json.getJSONArray("models")
        val list = mutableListOf<ModelInfo>()
        for (i in 0 until models.length()) {
            val obj = models.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.optString("name", id)
            list.add(
                ModelInfo(
                    providerId = providerId,
                    modelId = id,
                    name = name,
                    description = obj.optString("description", null),
                    maxTokens = obj.optInt("max_tokens", -1).takeIf { it > 0 },
                    capabilities = emptySet()
                )
            )
        }
        return list
    }
}
