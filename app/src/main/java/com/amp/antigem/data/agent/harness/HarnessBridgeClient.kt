package com.amp.antigem.data.agent.harness

import com.amp.antigem.data.agent.opencode.bool
import com.amp.antigem.data.agent.opencode.long
import com.amp.antigem.data.agent.opencode.o
import com.amp.antigem.data.agent.opencode.obj
import com.amp.antigem.data.agent.opencode.str
import com.amp.antigem.data.preferences.AuthPreferences
import com.amp.antigem.data.remote.AgyBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

val HarnessJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; isLenient = true }

class HarnessUnavailableException(message: String, val disabled: Boolean = false) : Exception(message)

class HarnessRpcException(message: String, val code: Int? = null) : Exception(message)

@Serializable
data class HarnessStatus(
    val success: Boolean = false,
    val installed: Boolean = false,
    val running: Boolean = false,
    val binary: String? = null,
    val error: String? = null,
    val logs: List<String>? = null
)

class HarnessBridgeClient {
    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String = "http://127.0.0.1:${AgyBridgeService.activePort}/api/harness"

    private fun wsUrl(): String = "ws://127.0.0.1:${AgyBridgeService.activePort}/api/harness/ws"

    suspend fun status(): HarnessStatus = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${baseUrl()}/status")
            .header("X-CSRF-Token", AuthPreferences.csrfToken)
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 503) throw HarnessUnavailableException("DeepSeek Harness is disabled", disabled = true)
            HarnessJson.decodeFromString(HarnessStatus.serializer(), body)
        }
    }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${baseUrl()}/start")
            .header("X-CSRF-Token", AuthPreferences.csrfToken)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun rpc(method: String, params: JsonElement? = null, timeoutMs: Int = 60000): JsonElement = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("method", method)
            if (params != null) put("params", params)
            put("timeout_ms", timeoutMs)
        }
        val req = Request.Builder()
            .url("${baseUrl()}/rpc")
            .header("X-CSRF-Token", AuthPreferences.csrfToken)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val obj = HarnessJson.parseToJsonElement(raw).obj
            val success = obj.bool("success") ?: false
            if (!success) {
                val err = obj.str("error") ?: "RPC call failed"
                throw HarnessRpcException(err)
            }
            obj["result"] ?: JsonObject(emptyMap())
        }
    }

    fun events(sinceSeq: Long = 0): Flow<String> = callbackFlow {
        val url = "${wsUrl()}?since=$sinceSeq"
        val req = Request.Builder()
            .url(url)
            .header("X-CSRF-Token", AuthPreferences.csrfToken)
            .build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }

        val ws = http.newWebSocket(req, listener)
        awaitClose { ws.cancel() }
    }.buffer(Channel.BUFFERED)
}
