package com.amp.antigem.ui.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amp.antigem.data.provider.ApiProtocol
import com.amp.antigem.data.provider.CustomProviderConfig
import com.amp.antigem.data.provider.CustomProviderStore
import com.amp.antigem.theme.QuotaGreen
import com.amp.antigem.theme.QuotaRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val Accent = Color(0xFF7C3AED)
private val SheetBg = Color(0xFF13131E)

/** Result of a quick connection probe against <baseUrl>/v1/models. */
private sealed interface TestResult {
    data class Success(val modelCount: Int, val latencyMs: Long) : TestResult
    data class Failure(val message: String) : TestResult
}

/** Calls <baseUrl>/v1/models with the supplied API key and returns a [TestResult]. */
private suspend fun probeConnection(baseUrl: String, apiKey: String): TestResult =
    withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val url = URL("${baseUrl.trimEnd('/')}/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val body = runCatching { conn.inputStream.bufferedReader().readText() }.getOrElse { "" }
            conn.disconnect()
            val latency = System.currentTimeMillis() - t0
            if (code in 200..299) {
                // rough count: number of "\"id\"" occurrences as a heuristic
                val modelCount = body.split("\"id\"").size - 1
                TestResult.Success(modelCount.coerceAtLeast(0), latency)
            } else {
                TestResult.Failure("HTTP $code${body.take(120).takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""}")
            }
        } catch (e: Exception) {
            TestResult.Failure(e.message ?: "Network error")
        }
    }

/**
 * Bottom sheet for adding a new custom provider (OpenAI-compatible or Anthropic-compatible).
 * On "Add Provider" the config is persisted via [CustomProviderStore] and [onProviderAdded] is called.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomProviderSheet(
    onDismiss: () -> Unit,
    onProviderAdded: (CustomProviderConfig) -> Unit,
    prefill: CustomProviderConfig? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(prefill?.name ?: "") }
    var baseUrl by remember { mutableStateOf(prefill?.apiBaseUrl ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var protocol by remember { mutableStateOf(prefill?.protocol ?: ApiProtocol.OPENAI_COMPAT) }
    var modelsText by remember { mutableStateOf(prefill?.customModels?.joinToString(", ") ?: "") }

    var protocolMenuOpen by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val canAdd = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── header ──────────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Add Provider",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Dismiss", tint = muted)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // ── provider name ────────────────────────────────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Provider Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── base URL ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; testResult = null },
                label = { Text("API Base URL") },
                singleLine = true,
                placeholder = { Text("https://api.example.com", color = muted) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            // ── API key ──────────────────────────────────────────────────────────
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; testResult = null },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (keyVisible) "Hide key" else "Show key",
                            tint = muted
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ── protocol dropdown ────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (protocol == ApiProtocol.OPENAI_COMPAT) "OpenAI-compatible" else "Anthropic-compatible",
                    onValueChange = {},
                    label = { Text("Protocol") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { protocolMenuOpen = true }) {
                            Icon(Icons.Filled.ArrowDropDown, "Select protocol")
                        }
                    }
                )
                DropdownMenu(
                    expanded = protocolMenuOpen,
                    onDismissRequest = { protocolMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("OpenAI-compatible") },
                        onClick = { protocol = ApiProtocol.OPENAI_COMPAT; protocolMenuOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Anthropic-compatible") },
                        onClick = { protocol = ApiProtocol.ANTHROPIC_COMPAT; protocolMenuOpen = false }
                    )
                }
            }

            // ── optional models ──────────────────────────────────────────────────
            OutlinedTextField(
                value = modelsText,
                onValueChange = { modelsText = it },
                label = { Text("Model IDs (optional)") },
                singleLine = true,
                placeholder = { Text("model-id-1, model-id-2", color = muted) },
                supportingText = { Text("Comma-separated. Leave blank to discover models automatically.", color = muted, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            // ── test connection ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = !testing && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                    onClick = {
                        scope.launch {
                            testing = true
                            testResult = null
                            testResult = probeConnection(baseUrl, apiKey)
                            testing = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.NetworkCheck, null, Modifier.size(16.dp), tint = Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Test Connection", color = Accent)
                }
                if (testing) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                }
            }

            // ── test result ──────────────────────────────────────────────────────
            testResult?.let { result ->
                val ok = result is TestResult.Success
                val color = if (ok) QuotaGreen else QuotaRed
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            null, tint = color, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (result is TestResult.Success) {
                                Text("✓ Connected · ${result.latencyMs} ms", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = color)
                                if (result.modelCount > 0)
                                    Text("~${result.modelCount} model(s) listed", fontSize = 11.5.sp, color = muted)
                            } else if (result is TestResult.Failure) {
                                Text("✗ Connection failed", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = color)
                                Text(result.message, fontSize = 11.5.sp, color = muted)
                            }
                        }
                    }
                }
            }

            saveError?.let { err ->
                Text(err, color = QuotaRed, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            // ── add provider button ──────────────────────────────────────────────
            Button(
                enabled = canAdd,
                onClick = {
                    if (name.isBlank()) { saveError = "Provider name is required"; return@Button }
                    if (baseUrl.isBlank()) { saveError = "Base URL is required"; return@Button }
                    if (apiKey.isBlank()) { saveError = "API Key is required"; return@Button }
                    saveError = null
                    val models = modelsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val config = CustomProviderConfig(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        apiBaseUrl = baseUrl.trim(),
                        protocol = protocol,
                        customModels = models
                    )
                    val existing = CustomProviderStore.loadAll(context)
                    CustomProviderStore.saveAll(context, existing + config)
                    CustomProviderStore.saveApiKey(context, config.id, apiKey.trim())
                    onProviderAdded(config)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Add Provider", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
