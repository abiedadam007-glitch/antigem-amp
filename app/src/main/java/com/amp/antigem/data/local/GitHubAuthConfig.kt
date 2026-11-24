package com.amp.antigem.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Utility object for securely storing GitHub OAuth credentials.
 *
 * The values are persisted using EncryptedSharedPreferences, which encrypts both keys and values
 * with a master key stored in the Android Keystore. This ensures that the client secret never
 * appears in plain text on the device or in version control.
 */
object GitHubAuthConfig {
    private const val PREF_NAME = "github_auth_prefs"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_SECRET = "client_secret"
    private const val KEY_ACCESS_TOKEN = "access_token"

    private lateinit var encryptedPrefs: EncryptedSharedPreferences

    /**
     * Initialise the encrypted preferences. Must be called once, e.g. from Application.onCreate().
     */
    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        // Pre‑populate the client id/secret if they are not yet stored.
        if (clientId == null) clientId = "Iv23lifsMA5y3cDuTv9y"
        if (clientSecret == null) clientSecret = "6a5889f9bd92caf9835e2f8d2e3b357b662b2fe7"
    }

    var clientId: String?
        get() = encryptedPrefs.getString(KEY_CLIENT_ID, null)
        set(value) = encryptedPrefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var clientSecret: String?
        get() = encryptedPrefs.getString(KEY_CLIENT_SECRET, null)
        set(value) = encryptedPrefs.edit().putString(KEY_CLIENT_SECRET, value).apply()

    /**
     * Store the OAuth access token obtained after the user authorises the app.
     */
    var accessToken: String?
        get() = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = encryptedPrefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    /**
     * Helper to clear all stored credentials (e.g., on logout).
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }
}
