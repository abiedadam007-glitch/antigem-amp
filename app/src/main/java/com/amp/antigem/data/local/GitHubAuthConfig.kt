package com.amp.antigem.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility object for securely storing GitHub OAuth credentials in private SharedPreferences.
 */
object GitHubAuthConfig {
    private const val PREF_NAME = "github_auth_prefs"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_SECRET = "client_secret"
    private const val KEY_ACCESS_TOKEN = "access_token"

    private lateinit var prefs: SharedPreferences

    /**
     * Initialise the preferences. Must be called once from Application.onCreate().
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Pre-populate the client id/secret if they are not yet stored.
        if (clientId.isNullOrBlank()) clientId = "Iv23lifsMA5y3cDuTv9y"
        if (clientSecret.isNullOrBlank()) clientSecret = "6a5889f9bd92caf9835e2f8d2e3b357b662b2fe7"
    }

    var clientId: String?
        get() = if (::prefs.isInitialized) prefs.getString(KEY_CLIENT_ID, "Iv23lifsMA5y3cDuTv9y") else "Iv23lifsMA5y3cDuTv9y"
        set(value) = if (::prefs.isInitialized) prefs.edit().putString(KEY_CLIENT_ID, value).apply() else Unit

    var clientSecret: String?
        get() = if (::prefs.isInitialized) prefs.getString(KEY_CLIENT_SECRET, "6a5889f9bd92caf9835e2f8d2e3b357b662b2fe7") else "6a5889f9bd92caf9835e2f8d2e3b357b662b2fe7"
        set(value) = if (::prefs.isInitialized) prefs.edit().putString(KEY_CLIENT_SECRET, value).apply() else Unit

    /**
     * Store the OAuth access token obtained after the user authorises the app.
     */
    var accessToken: String?
        get() = if (::prefs.isInitialized) prefs.getString(KEY_ACCESS_TOKEN, null) else null
        set(value) = if (::prefs.isInitialized) prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply() else Unit

    /**
     * Helper to clear all stored credentials (e.g., on logout).
     */
    fun clearAll() {
        if (::prefs.isInitialized) {
            prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
        }
    }
}
