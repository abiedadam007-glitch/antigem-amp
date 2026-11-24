package com.amp.antigem.data.provider

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.amp.antigem.data.local.GitHubAuthConfig

/**
 * Singleton managing all AI providers (OpenCode, Codex, Claude, DeepSeek, Custom).
 * Stores ProviderConfig in a StateFlow so UI can observe changes.
 */
object ProviderManager {
    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providers: StateFlow<List<ProviderConfig>> = _providers

    /** Initialise with built‑in providers. Call from Application.onCreate(). */
    fun initialise(context: Context) {
        // Ensure GitHubAuthConfig is ready (it will load stored client id/secret).
        GitHubAuthConfig.init(context)
        // Load persisted providers from SharedPreferences (or create defaults).
        // For brevity we create defaults now; persistence can be added later.
        val defaultProviders = listOf(
            ProviderConfig(
                id = "opencode",
                name = "OpenCode",
                type = ProviderType.OPENAI_COMPAT,
                auth = AuthMethod.API_KEY,
                apiKeyEncrypted = null,
                installPath = null,
                status = ProviderStatus.NOT_INSTALLED,
                supportedModels = emptyList()
            ),
            ProviderConfig(
                id = "codex",
                name = "Codex",
                type = ProviderType.OPENAI_COMPAT,
                auth = AuthMethod.API_KEY,
                apiKeyEncrypted = null,
                installPath = null,
                status = ProviderStatus.NOT_INSTALLED,
                supportedModels = emptyList()
            ),
            ProviderConfig(
                id = "claude",
                name = "Claude",
                type = ProviderType.ANTHROPIC_COMPAT,
                auth = AuthMethod.API_KEY,
                apiKeyEncrypted = null,
                installPath = null,
                status = ProviderStatus.NOT_INSTALLED,
                supportedModels = emptyList()
            ),
            ProviderConfig(
                id = "deepseek",
                name = "DeepSeek Harness",
                type = ProviderType.DEEPSEEK_HARNESS,
                auth = AuthMethod.API_KEY,
                apiKeyEncrypted = null,
                installPath = null,
                status = ProviderStatus.NOT_INSTALLED,
                supportedModels = emptyList()
            )
        )
        _providers.value = defaultProviders
    }

    /** Update a provider configuration (e.g., after install or after saving an API key). */
    fun updateProvider(updated: ProviderConfig) {
        val current = _providers.value.toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            current[index] = updated
            _providers.value = current
        }
    }

    /** Convenience to set API key (encrypted) for a provider. */
    fun setApiKey(providerId: String, apiKey: String) {
        // Simple encryption placeholder – in real code use EncryptedSharedPreferences.
        val encrypted = apiKey.reversed() // NOT SECURE – replace with proper encryption later.
        val provider = _providers.value.find { it.id == providerId } ?: return
        val updated = provider.copy(apiKeyEncrypted = encrypted)
        updateProvider(updated)
    }

    /** Retrieve decrypted API key – real implementation should use secure storage. */
    fun getApiKey(providerId: String): String? {
        val provider = _providers.value.find { it.id == providerId } ?: return null
        return provider.apiKeyEncrypted?.reversed()
    }
}

/**
 * Data class representing a provider configuration.
 */
data class ProviderConfig(
    val id: String,               // Unique identifier, e.g., "deepseek"
    val name: String,
    val type: ProviderType,
    val auth: AuthMethod,
    val apiKeyEncrypted: String?,
    val installPath: String?,
    val status: ProviderStatus,
    val supportedModels: List<ModelInfo>
)

enum class ProviderType {
    OPENAI_COMPAT,
    ANTHROPIC_COMPAT,
    DEEPSEEK_HARNESS,
    CUSTOM
}

enum class AuthMethod {
    OAUTH,
    API_KEY,
    NONE
}

enum class ProviderStatus {
    NOT_INSTALLED,
    INSTALLING,
    INSTALLED,
    UPDATE_AVAILABLE,
    FAILED
}

/** Simple placeholder ModelInfo – real definition lives in ModelManager.kt */
data class ModelInfo(
    val providerId: String,
    val modelId: String,
    val name: String,
    val description: String? = null,
    val maxTokens: Int? = null,
    val capabilities: Set<String> = emptySet()
)
