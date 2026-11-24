package com.amp.antigem.data.github

import android.content.Context
import android.net.Uri
import com.amp.antigem.data.local.GitHubAuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Manages GitHub App OAuth 2.0 authentication.
 *
 * Uses [GitHubAuthConfig] for the client_id / client_secret (backed by EncryptedSharedPreferences)
 * and stores the resulting access token in that same encrypted store.
 *
 * Flow:
 *   1. Call [buildAuthUrl] → open the URL in a browser / Custom Tab.
 *   2. GitHub redirects to `antigem://oauth/callback?code=…&state=…`.
 *   3. Call [exchangeCode] with the `code` query parameter.
 *   4. Use [getUser] to fetch the authenticated account.
 */
object GitHubOAuthManager {

    private const val REDIRECT_URI = "antigem://oauth/callback"
    private const val SCOPE = "repo,read:user,user:email"

    // ── State ─────────────────────────────────────────────────────────────────

    data class State(
        val isAuthenticated: Boolean = false,
        val isLoading: Boolean = false,
        val user: GitHubUser? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // Last PKCE-style state nonce, checked when the callback arrives.
    private var pendingState: String? = null

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Must be called once (e.g. from Application.onCreate or the first time the
     * GitHub OAuth screen is composed) after [GitHubAuthConfig.init] has already
     * been called.
     */
    fun init(context: Context) {
        GitHubAuthConfig.init(context)
        _state.value = _state.value.copy(isAuthenticated = GitHubAuthConfig.accessToken != null)
    }

    // ── Auth URL ──────────────────────────────────────────────────────────────

    /**
     * Builds the GitHub OAuth authorization URL and stores the random state
     * nonce so it can be verified in [handleCallback].
     */
    fun buildAuthUrl(): String {
        val state = buildState()
        pendingState = state
        val clientId = GitHubAuthConfig.clientId ?: error("GitHubAuthConfig not initialised")
        return Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .path("/login/oauth/authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .build()
            .toString()
    }

    private fun buildState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Callback handling ─────────────────────────────────────────────────────

    /**
     * Processes an incoming OAuth callback URI (e.g. `antigem://oauth/callback?code=…&state=…`).
     * Returns `true` if the URI was recognised as an OAuth callback (even if it then fails).
     */
    suspend fun handleCallback(uri: Uri): Boolean {
        if (uri.scheme != "antigem" || uri.host != "oauth" || uri.path != "/callback") return false

        val error = uri.getQueryParameter("error")
        if (error != null) {
            val desc = uri.getQueryParameter("error_description") ?: error
            _state.value = _state.value.copy(isLoading = false, error = desc)
            return true
        }

        val code = uri.getQueryParameter("code") ?: return false
        val state = uri.getQueryParameter("state")
        if (state != pendingState) {
            _state.value = _state.value.copy(isLoading = false, error = "State mismatch — please try again.")
            return true
        }
        pendingState = null

        exchangeCode(code)
        return true
    }

    // ── Token exchange ────────────────────────────────────────────────────────

    /**
     * Exchanges [code] for an access token and stores it encrypted.
     * Updates [state] on success/failure.
     */
    suspend fun exchangeCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        runCatching {
            val clientId = GitHubAuthConfig.clientId ?: error("Missing client_id")
            val clientSecret = GitHubAuthConfig.clientSecret ?: error("Missing client_secret")

            val body = "client_id=${Uri.encode(clientId)}" +
                "&client_secret=${Uri.encode(clientSecret)}" +
                "&code=${Uri.encode(code)}" +
                "&redirect_uri=${Uri.encode(REDIRECT_URI)}"

            val conn = URL("https://github.com/login/oauth/access_token")
                .openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            val responseBody = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (responseCode !in 200..299) {
                error("Token exchange failed ($responseCode): $responseBody")
            }

            val json = JSONObject(responseBody)
            val errorField = json.optString("error")
            if (errorField.isNotBlank()) {
                error(json.optString("error_description").ifBlank { errorField })
            }

            val token = json.getString("access_token")
            GitHubAuthConfig.accessToken = token
            _state.value = _state.value.copy(isAuthenticated = true, isLoading = false)

            // Eagerly load the user profile
            loadUser()

            token
        }.onFailure { e ->
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Authentication failed"
            )
        }
    }

    // ── User info ─────────────────────────────────────────────────────────────

    /**
     * Fetches the authenticated user from `https://api.github.com/user`.
     * Updates [state] with the result.
     */
    suspend fun getUser(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        runCatching {
            loadUser()!!
        }.onFailure { e ->
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to load user"
            )
        }
    }

    private suspend fun loadUser(): GitHubUser? = withContext(Dispatchers.IO) {
        val token = GitHubAuthConfig.accessToken ?: return@withContext null
        val conn = URL("https://api.github.com/user").openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        val responseCode = conn.responseCode
        val responseBody = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        conn.disconnect()

        if (responseCode == 401) {
            // Token revoked — clear it
            GitHubAuthConfig.accessToken = null
            _state.value = State(isAuthenticated = false, error = "Session expired — please reconnect.")
            return@withContext null
        }

        if (responseCode !in 200..299) {
            error("GitHub API error ($responseCode): $responseBody")
        }

        val json = JSONObject(responseBody)
        val user = GitHubUser(
            login = json.getString("login"),
            name = json.optString("name").ifBlank { null },
            avatarUrl = json.optString("avatar_url").ifBlank { null },
            email = json.optString("email").ifBlank { null },
        )
        _state.value = _state.value.copy(isLoading = false, user = user)
        user
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    fun isAuthenticated(): Boolean = GitHubAuthConfig.accessToken != null

    fun logout() {
        GitHubAuthConfig.accessToken = null
        _state.value = State(isAuthenticated = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

// ── Data model ────────────────────────────────────────────────────────────────

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val email: String?,
)
