package com.amp.antigem.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amp.antigem.data.github.GitHubOAuthManager
import com.amp.antigem.data.github.GitHubUser
import com.amp.antigem.theme.QuotaGreen
import kotlinx.coroutines.launch

/**
 * Settings section — GitHub App OAuth login.
 *
 * Shows:
 *  • A "Connect via GitHub App OAuth" card when not authenticated.
 *  • A connected-account card (avatar, username, email) with a Disconnect button.
 *  • A loading overlay during the OAuth exchange.
 *
 * The card style deliberately mirrors [GitHubScreen] (same [cardBg] / [cardBorder]
 * parameters, same 16 dp card corners, same font sizes and spacing).
 */
@Composable
fun GitHubOAuthScreen(cardBg: Color, cardBorder: BorderStroke) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        GitHubOAuthManager.init(context)
        // Reload the user profile if a token is already stored.
        if (GitHubOAuthManager.isAuthenticated()) {
            GitHubOAuthManager.getUser()
        }
    }

    val state by GitHubOAuthManager.state.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Error banner ──────────────────────────────────────────────────────
        state.error?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = GitHubOAuthManager::clearError) {
                        Icon(Icons.Default.Close, "Dismiss", Modifier.size(18.dp))
                    }
                }
            }
        }

        // ── Main card ─────────────────────────────────────────────────────────
        when {
            state.isLoading -> OAuthLoadingCard(cardBg, cardBorder)

            state.isAuthenticated && state.user != null ->
                OAuthConnectedCard(state.user!!, cardBg, cardBorder,
                    onDisconnect = { GitHubOAuthManager.logout() })

            state.isAuthenticated ->
                // Token exists but we don't have the user yet — show loading
                OAuthLoadingCard(cardBg, cardBorder)

            else ->
                OAuthConnectCard(cardBg, cardBorder, onConnect = {
                    val url = GitHubOAuthManager.buildAuthUrl()
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                })
        }
    }
}

// ─── Connect card ──────────────────────────────────────────────────────────────

@Composable
private fun OAuthConnectCard(cardBg: Color, cardBorder: BorderStroke, onConnect: () -> Unit) {
    val onSurfaceDim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Connect via GitHub App OAuth",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Sign in with your GitHub account using the official OAuth flow.",
                        fontSize = 12.sp,
                        color = onSurfaceDim,
                        lineHeight = 17.sp
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Text(
                "Grants access to your repositories and profile. No password required.",
                fontSize = 12.sp,
                color = onSurfaceDim,
                lineHeight = 16.sp
            )

            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF238636),   // GitHub green
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect GitHub", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Connected card ────────────────────────────────────────────────────────────

@Composable
private fun OAuthConnectedCard(
    user: GitHubUser,
    cardBg: Color,
    cardBorder: BorderStroke,
    onDisconnect: () -> Unit
) {
    val onSurfaceDim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Account row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "${user.login}'s avatar",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    )
                } else {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(QuotaGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            tint = QuotaGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        user.name ?: user.login,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "@${user.login}",
                        fontSize = 12.sp,
                        color = onSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    user.email?.let {
                        Text(it, fontSize = 11.5.sp, color = onSurfaceDim, maxLines = 1)
                    }
                }

                // Connected pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = QuotaGreen.copy(alpha = 0.12f)
                ) {
                    Text(
                        "Connected",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QuotaGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Disconnect button
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Disconnect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Loading card ──────────────────────────────────────────────────────────────

@Composable
private fun OAuthLoadingCard(cardBg: Color, cardBorder: BorderStroke) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Text(
                "Connecting to GitHub…",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}
