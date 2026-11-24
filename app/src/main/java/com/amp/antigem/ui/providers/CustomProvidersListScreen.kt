package com.amp.antigem.ui.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amp.antigem.data.provider.ApiProtocol
import com.amp.antigem.data.provider.CustomProviderConfig
import com.amp.antigem.data.provider.CustomProviderStore

/**
 * Screen displaying both preset and user-added custom AI providers.
 * Supports OpenAI-compatible and Anthropic-compatible protocols.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProvidersListScreen(
    cardBg: Color = Color(0xFF13131E),
    cardBorder: BorderStroke = BorderStroke(1.dp, Color(0xFF252538))
) {
    val context = LocalContext.current
    var providers by remember { mutableStateOf<List<CustomProviderConfig>>(emptyList()) }
    var showAddSheet by remember { mutableStateOf(false) }
    var presetToPreFill by remember { mutableStateOf<CustomProviderConfig?>(null) }

    LaunchedEffect(Unit) {
        providers = CustomProviderStore.loadAll(context)
    }

    val presets = listOf(
        CustomProviderConfig(
            id = "openrouter",
            name = "OpenRouter",
            apiBaseUrl = "https://openrouter.ai/api/v1",
            protocol = ApiProtocol.OPENAI_COMPAT
        ),
        CustomProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            apiBaseUrl = "https://api.deepseek.com/v1",
            protocol = ApiProtocol.OPENAI_COMPAT
        ),
        CustomProviderConfig(
            id = "kimi",
            name = "Kimi (Moonshot)",
            apiBaseUrl = "https://api.moonshot.cn/v1",
            protocol = ApiProtocol.OPENAI_COMPAT
        ),
        CustomProviderConfig(
            id = "zai",
            name = "Z.ai",
            apiBaseUrl = "https://api.z.ai/v1",
            protocol = ApiProtocol.ANTHROPIC_COMPAT
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Presets Section
        Text(
            text = "Quick Presets",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = {
                        presetToPreFill = preset
                        showAddSheet = true
                    },
                    label = { Text(preset.name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E1E2E),
                        labelColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFF33334D))
                )
            }
        }

        // Add Provider Action
        Button(
            onClick = {
                presetToPreFill = null
                showAddSheet = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Custom Provider", fontWeight = FontWeight.Bold)
        }

        // Configured Providers List
        Text(
            text = "Configured Providers (${providers.size})",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        if (providers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No custom providers added yet",
                        fontSize = 14.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Text(
                        "Tap a preset above or add any OpenAI/Anthropic compatible provider",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        } else {
            providers.forEach { provider ->
                val hasKey = !CustomProviderStore.getApiKey(context, provider.id).isNullOrBlank()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = cardBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = provider.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2E2E3E))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (provider.protocol == ApiProtocol.OPENAI_COMPAT) "OpenAI" else "Anthropic",
                                        fontSize = 10.sp,
                                        color = Color(0xFFA78BFA),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = provider.apiBaseUrl,
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF)
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = if (hasKey) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (hasKey) "API Key Set" else "No Key",
                                    fontSize = 11.sp,
                                    color = if (hasKey) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val updated = providers.filter { it.id != provider.id }
                                providers = updated
                                CustomProviderStore.saveAll(context, updated)
                                CustomProviderStore.deleteApiKey(context, provider.id)
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddCustomProviderSheet(
            onDismiss = { showAddSheet = false },
            onProviderAdded = { newProvider ->
                val updated = providers.filter { it.id != newProvider.id } + newProvider
                providers = updated
                CustomProviderStore.saveAll(context, updated)
                showAddSheet = false
            }
        )
    }
}
