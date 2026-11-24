package com.amp.antigem.ui.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amp.antigem.data.agent.harness.HarnessPreferences

/**
 * Compact chat controls bar for DeepSeek Harness (showing active model and profile,
 * with quick access to the harness settings screen).
 */
@Composable
fun HarnessChatControls(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { HarnessPreferences(context) }
    val model by prefs.model.collectAsState()
    val profile by prefs.profile.collectAsState()

    val modelLabel = when (model) {
        "deepseek-reasoner" -> "DeepSeek R1"
        "deepseek-coder" -> "DeepSeek Coder"
        else -> "DeepSeek V3"
    }

    val profileLabel = when (profile) {
        "headless" -> "Headless"
        "web" -> "Web"
        else -> "ACP"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF13131E))
            .border(1.dp, Color(0xFF252538), RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🐋", fontSize = 14.sp)
        Text(
            text = modelLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF7C3AED).copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = profileLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA78BFA)
            )
        }
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Harness Settings",
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(14.dp)
        )
    }
}
