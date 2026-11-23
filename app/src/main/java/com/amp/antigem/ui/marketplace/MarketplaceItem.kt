package com.amp.antigem.ui.marketplace

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class MarketplaceAuthType {
    NONE,
    API_KEY,
    OAUTH,
    CREDENTIALS
}

@Serializable
data class MarketplaceItem(
    val id: String,
    val name: String,
    val description: String,
    val iconEmoji: String,
    val iconColor: Long = 0xFF3B82F6,
    val authType: MarketplaceAuthType = MarketplaceAuthType.NONE,
    val authPrompt: String? = null,
    val authHelpUrl: String? = null,
    val category: String = "general",
    val isInstalled: Boolean = false,
    val isPopular: Boolean = false,
    val isNew: Boolean = false
)

object MarketplaceRepository {
    private const val PREFS_NAME = "marketplace_prefs"
    private const val KEY_INSTALLED_PREFIX = "installed_"

    val POPULAR_ITEMS = listOf(
        MarketplaceItem(
            id = "gmail",
            name = "Gmail",
            description = "Read and manage Gmail",
            iconEmoji = "✉️",
            iconColor = 0xFFEA4335,
            authType = MarketplaceAuthType.OAUTH,
            authPrompt = "Google Account / OAuth Access Token",
            authHelpUrl = "https://myaccount.google.com/apppasswords",
            category = "mail",
            isPopular = true
        ),
        MarketplaceItem(
            id = "google_drive",
            name = "Google Drive",
            description = "Drive, Docs, Sheets or Slides",
            iconEmoji = "📁",
            iconColor = 0xFF34A853,
            authType = MarketplaceAuthType.OAUTH,
            authPrompt = "Google Drive Access Token",
            authHelpUrl = "https://console.cloud.google.com",
            category = "storage",
            isPopular = true
        ),
        MarketplaceItem(
            id = "remote_desktop",
            name = "Remote Desktop Commander",
            description = "Use your computer from chat",
            iconEmoji = "🖥️",
            iconColor = 0xFF4B5563,
            authType = MarketplaceAuthType.CREDENTIALS,
            authPrompt = "Host IP and Port (e.g. 192.168.1.50:5900)",
            category = "system",
            isPopular = true
        ),
        MarketplaceItem(
            id = "health",
            name = "Health",
            description = "Explore your health data",
            iconEmoji = "❤️",
            iconColor = 0xFFEF4444,
            authType = MarketplaceAuthType.NONE,
            category = "health",
            isPopular = true
        ),
        MarketplaceItem(
            id = "github",
            name = "GitHub",
            description = "Manage repos, pull requests and issues",
            iconEmoji = "🐙",
            iconColor = 0xFF24292F,
            authType = MarketplaceAuthType.API_KEY,
            authPrompt = "GitHub Personal Access Token (PAT)",
            authHelpUrl = "https://github.com/settings/tokens",
            category = "dev",
            isPopular = true
        )
    )

    val NEW_ITEMS = listOf(
        MarketplaceItem(
            id = "icloud_mail",
            name = "iCloud Mail",
            description = "Read and manage iCloud email",
            iconEmoji = "📫",
            iconColor = 0xFF0284C7,
            authType = MarketplaceAuthType.CREDENTIALS,
            authPrompt = "Apple ID & App-Specific Password",
            authHelpUrl = "https://appleid.apple.com",
            category = "mail",
            isNew = true
        ),
        MarketplaceItem(
            id = "canva",
            name = "Canva",
            description = "Generate designs, presentations and graphics",
            iconEmoji = "🎨",
            iconColor = 0xFF00C4CC,
            authType = MarketplaceAuthType.API_KEY,
            authPrompt = "Canva Connect API Key",
            authHelpUrl = "https://www.canva.com/developers/",
            category = "design",
            isNew = true
        ),
        MarketplaceItem(
            id = "brave_search",
            name = "Brave Web Search",
            description = "Live internet search and real-time research",
            iconEmoji = "🔍",
            iconColor = 0xFFFB542B,
            authType = MarketplaceAuthType.API_KEY,
            authPrompt = "Brave Search API Key",
            authHelpUrl = "https://brave.com/search/api/",
            category = "research",
            isNew = true
        ),
        MarketplaceItem(
            id = "sqlite_mcp",
            name = "SQLite Database",
            description = "Query, inspect and manipulate local SQLite databases",
            iconEmoji = "🗄️",
            iconColor = 0xFF003B57,
            authType = MarketplaceAuthType.NONE,
            category = "data",
            isNew = true
        )
    )

    fun isInstalled(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INSTALLED_PREFIX + id, false)
    }

    fun setInstalled(context: Context, id: String, installed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INSTALLED_PREFIX + id, installed).apply()
    }
}
