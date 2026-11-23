package com.amp.antigem.ui.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarketplaceScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedAuthItem by remember { mutableStateOf<MarketplaceItem?>(null) }
    var installedIds by remember {
        mutableStateOf(
            (MarketplaceRepository.POPULAR_ITEMS + MarketplaceRepository.NEW_ITEMS)
                .filter { MarketplaceRepository.isInstalled(context, it.id) }
                .map { it.id }
                .toSet()
        )
    }

    val popularFiltered = remember(searchQuery, installedIds) {
        MarketplaceRepository.POPULAR_ITEMS.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val newFiltered = remember(searchQuery, installedIds) {
        MarketplaceRepository.NEW_ITEMS.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1F1F))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "المكونات الإضافية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Top Horizontal Quick Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                val carouselItems = listOf(
                    "⚡" to Color(0xFF10B981),
                    "☘️" to Color(0xFF059669),
                    "🐙" to Color(0xFF24292F),
                    "🎨" to Color(0xFF3B82F6),
                    "✨" to Color(0xFF00C4CC)
                )

                carouselItems.forEach { (emoji, bg) ->
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Section 1: Most Popular
            if (popularFiltered.isNotEmpty()) {
                SectionHeader(title = "الأكثر رواجًا")
                popularFiltered.forEach { item ->
                    MarketplaceRowItem(
                        item = item,
                        isInstalled = item.id in installedIds,
                        onActionClick = {
                            if (item.id in installedIds) {
                                MarketplaceRepository.setInstalled(context, item.id, false)
                                installedIds = installedIds - item.id
                            } else if (item.authType != MarketplaceAuthType.NONE) {
                                selectedAuthItem = item
                            } else {
                                MarketplaceRepository.setInstalled(context, item.id, true)
                                installedIds = installedIds + item.id
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Section 2: New & Noteworthy
            if (newFiltered.isNotEmpty()) {
                SectionHeader(title = "الجديد والجدير بالاهتمام")
                newFiltered.forEach { item ->
                    MarketplaceRowItem(
                        item = item,
                        isInstalled = item.id in installedIds,
                        onActionClick = {
                            if (item.id in installedIds) {
                                MarketplaceRepository.setInstalled(context, item.id, false)
                                installedIds = installedIds - item.id
                            } else if (item.authType != MarketplaceAuthType.NONE) {
                                selectedAuthItem = item
                            } else {
                                MarketplaceRepository.setInstalled(context, item.id, true)
                                installedIds = installedIds + item.id
                            }
                        }
                    )
                }
            }
        }

        // Floating Bottom Search Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                color = Color(0xFF1E1E1E),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "بحث في المكونات الإضافية",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 15.sp
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    selectedAuthItem?.let { item ->
        MarketplaceAuthDialog(
            item = item,
            onDismiss = { selectedAuthItem = null },
            onSuccess = {
                MarketplaceRepository.setInstalled(context, item.id, true)
                installedIds = installedIds + item.id
                selectedAuthItem = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun MarketplaceRowItem(
    item: MarketplaceItem,
    isInstalled: Boolean,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActionClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Trailing button (left or right depending on RTL)
        IconButton(
            onClick = onActionClick,
            modifier = Modifier.size(36.dp)
        ) {
            when {
                isInstalled -> {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Installed",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                }
                item.authType != MarketplaceAuthType.NONE -> {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Auth Required",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Install",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Title and description
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = item.description,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1
            )
        }

        Spacer(Modifier.width(16.dp))

        // App Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(item.iconColor).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = item.iconEmoji, fontSize = 24.sp)
        }
    }
}
