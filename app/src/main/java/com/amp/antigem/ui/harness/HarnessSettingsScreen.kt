package com.amp.antigem.ui.harness

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amp.antigem.data.agent.harness.HarnessBridgeClient
import com.amp.antigem.data.agent.harness.HarnessChatBackend
import com.amp.antigem.data.agent.harness.HarnessPreferences
import com.amp.antigem.data.agent.harness.HarnessStatus
import com.amp.antigem.theme.QuotaGreen
import com.amp.antigem.theme.QuotaRed
import com.amp.antigem.ui.claude.ClaudeCard
import kotlinx.coroutines.launch

// ─── Accent & palette matching the dark premium UI ───────────────────────────
val HarnessAccent = Color(0xFF7C3AED)
private val CardBg = Color(0xFF131318)
private val Border = Color(0xFF2A2A3A)
private val Muted @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
}

/** A card using the Harness dark style (falls back to the shared ClaudeCard which uses AppColors.card). */
@Composable
private fun HarnessCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

/**
 * Full DeepSeek Harness settings screen – mirrors ClaudeSettingsScreen / OpenCodeSettingsScreen
 * but focused on the Harness specifics:
 *   • Install status + Install / Update button with live log
 *   • API key (password field)
 *   • Model radio buttons
 *   • Profile/Mode radio buttons
 *   • Test-connection button
 */
@Composable
fun HarnessSettingsScreen(
    backend: HarnessChatBackend? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { backend?.prefs ?: HarnessPreferences(context) }
    val client = remember { backend?.client ?: HarnessBridgeClient() }
    val scope = rememberCoroutineScope()

    // ── remote state ──────────────────────────────────────────────────────────
    var status by remember { mutableStateOf<HarnessStatus?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // install/update job (polled from /api/harness/status after triggering)
    var jobRunning by remember { mutableStateOf(false) }
    var jobLog by remember { mutableStateOf("") }
    var jobKind by remember { mutableStateOf("") }
    var jobExitCode by remember { mutableStateOf<Int?>(null) }

    // test-connection
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // ok, message

    // local prefs state
    val selectedModel by prefs.model.collectAsState()
    val selectedProfile by prefs.profile.collectAsState()
    val apiKey by prefs.apiKey.collectAsState()
    var apiKeyDraft by remember(apiKey) { mutableStateOf(apiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    // ── helpers ───────────────────────────────────────────────────────────────
    fun refresh() {
        scope.launch {
            refreshing = true
            statusError = null
            try {
                status = client.status()
            } catch (e: Exception) {
                statusError = e.message
            }
            refreshing = false
        }
    }

    fun startJob(kind: String) {
        scope.launch {
            jobKind = kind
            jobRunning = true
            jobLog = ""
            jobExitCode = null
            try {
                // POST /api/harness/install or /api/harness/update
                // The bridge triggers an async job; we poll status for progress
                val params = buildJsonObject {
                    put("kind", kind)
                }
                client.rpc("job.start", params)
                // poll until done
                var attempts = 0
                while (attempts < 120) {
                    kotlinx.coroutines.delay(2000)
                    attempts++
                    val s = try { client.status() } catch (_: Exception) { null }
                    if (s != null) {
                        status = s
                        jobLog = s.logs?.joinToString("\n").orEmpty()
                        if (!s.running) {
                            jobExitCode = if (s.success) 0 else 1
                            jobRunning = false
                            break
                        }
                    }
                }
                if (jobRunning) {
                    jobRunning = false
                    jobExitCode = -1
                }
            } catch (e: Exception) {
                jobLog = e.message.orEmpty()
                jobRunning = false
                jobExitCode = -1
            }
        }
    }

    fun testConnection() {
        scope.launch {
            testing = true
            testResult = null
            try {
                val t0 = System.currentTimeMillis()
                client.rpc("test.connection", timeoutMs = 10_000)
                val ms = System.currentTimeMillis() - t0
                testResult = true to "Connected · ${ms} ms"
            } catch (e: Exception) {
                testResult = false to (e.message ?: "Connection failed")
            }
            testing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        HarnessCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // whale emoji badge
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HarnessAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🐋", fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("DeepSeek Harness", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Autonomous coding with DeepSeek V3, R1 and Coder via ACP",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // ── Install status card ───────────────────────────────────────────────
        HarnessCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, null, tint = HarnessAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("DeepSeek Harness CLI", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (refreshing)
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = HarnessAccent)
                else
                    IconButton(onClick = { refresh() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            }

            Spacer(Modifier.height(6.dp))
            val s = status
            when {
                statusError != null -> {
                    Text(statusError!!, fontSize = 12.sp, color = QuotaRed)
                }
                s == null -> {
                    Text("Checking…", fontSize = 12.5.sp, color = Muted)
                }
                !s.installed -> {
                    Text("Not installed", fontSize = 13.sp, color = QuotaRed, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Hint("Install the @deepseek-ai/dsh CLI to use DeepSeek Harness.")
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(QuotaGreen)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Installed" + if (s.running) " · running" else "",
                            fontSize = 13.sp,
                            color = QuotaGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    s.binary?.let {
                        Spacer(Modifier.height(4.dp))
                        Hint(it)
                    }
                }
            }

            // Install / Update button
            if (s != null && !jobRunning) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!s.installed) {
                        Button(
                            onClick = { startJob("install") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HarnessAccent, contentColor = Color.White)
                        ) { Text("Install Harness") }
                    } else {
                        OutlinedButton(
                            onClick = { startJob("update") },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Check for updates") }
                    }
                }
            }
            if (jobRunning) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HarnessAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${jobKind.replaceFirstChar { it.uppercase() }}: running…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Live log view
            if (jobLog.isNotBlank() || jobExitCode != null) {
                Spacer(Modifier.height(8.dp))
                jobExitCode?.let { code ->
                    Text(
                        if (code == 0) "✓ Finished successfully" else "✗ Finished with error (exit $code)",
                        fontSize = 12.sp,
                        color = if (code == 0) QuotaGreen else QuotaRed
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (jobLog.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            jobLog.lines().takeLast(30).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }
                }
            }
        }

        // ── API Key ───────────────────────────────────────────────────────────
        HarnessCard {
            SectionTitle("DeepSeek API Key")
            Spacer(Modifier.height(4.dp))
            Hint("Your key is stored in encrypted local preferences. Required for DeepSeek API calls.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKeyDraft,
                onValueChange = { apiKeyDraft = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("sk-…") },
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (apiKeyVisible) "Hide key" else "Show key"
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HarnessAccent,
                    cursorColor = HarnessAccent
                )
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    prefs.setApiKey(apiKeyDraft.trim())
                    Toast.makeText(context, "API key saved", Toast.LENGTH_SHORT).show()
                },
                enabled = apiKeyDraft.trim() != apiKey,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HarnessAccent, contentColor = Color.White)
            ) { Text("Save") }
        }

        // ── Model selection ───────────────────────────────────────────────────
        HarnessCard {
            SectionTitle("Model")
            Spacer(Modifier.height(4.dp))
            Hint("Selected model is passed to the dsh CLI for every new session.")
            Spacer(Modifier.height(6.dp))
            HarnessPreferences.AVAILABLE_MODELS.forEach { (value, label) ->
                val selected = value == selectedModel
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { prefs.setModel(value) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { prefs.setModel(value) },
                        colors = androidx.compose.material3.RadioButtonDefaults.colors(
                            selectedColor = HarnessAccent
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            label,
                            fontSize = 13.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Hint(value)
                    }
                }
            }
        }

        // ── Profile / Mode ────────────────────────────────────────────────────
        HarnessCard {
            SectionTitle("Profile / Mode")
            Spacer(Modifier.height(4.dp))
            Hint("ACP = Agent Control Protocol (default). Headless = no browser UI. Web = browser session.")
            Spacer(Modifier.height(6.dp))
            HARNESS_PROFILES.forEach { (value, label, desc) ->
                val selected = value == selectedProfile
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { prefs.setProfile(value) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { prefs.setProfile(value) },
                        colors = androidx.compose.material3.RadioButtonDefaults.colors(
                            selectedColor = HarnessAccent
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            label,
                            fontSize = 13.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Hint(desc)
                    }
                }
            }
        }

        // ── Test Connection ───────────────────────────────────────────────────
        HarnessCard {
            SectionTitle("Test Connection")
            Spacer(Modifier.height(4.dp))
            Hint("Pings the Harness bridge API to verify the backend is reachable.")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { testConnection() },
                    enabled = !testing,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.NetworkCheck, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test connection")
                }
                if (testing) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HarnessAccent)
                }
            }
            testResult?.let { (ok, msg) ->
                Spacer(Modifier.height(10.dp))
                val color = if (ok) QuotaGreen else QuotaRed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            null, tint = color, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(msg, fontSize = 12.sp, color = color)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Profile definitions ───────────────────────────────────────────────────────
private data class HarnessProfileDef(val value: String, val label: String, val description: String)

private val HARNESS_PROFILES = listOf(
    HarnessProfileDef("acp", "ACP", "Agent Control Protocol – default autonomous mode"),
    HarnessProfileDef("headless", "Headless", "No browser UI, CLI-only operation"),
    HarnessProfileDef("web", "Web Profile", "Uses a browser session for web-aware tasks")
)
