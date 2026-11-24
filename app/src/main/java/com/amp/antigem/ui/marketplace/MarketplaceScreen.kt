package com.amp.antigem.ui.marketplace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var selectedAuthItem by remember { mutableStateOf<MarketplaceItem?>(null) }
    var detailItem by remember { mutableStateOf<MarketplaceItem?>(null) }

    var installedIds by remember {
        mutableStateOf(
            (MarketplaceRepository.POPULAR_ITEMS + MarketplaceRepository.NEW_ITEMS)
                .filter { MarketplaceRepository.isInstalled(context, it.id) }
                .map { it.id }
                .toSet()
        )
    }

    val categories = listOf(
        "all" to "All",
        "dev" to "Development",
        "ai" to "AI & Models",
        "communication" to "Communication",
        "productivity" to "Productivity",
        "design" to "Design",
        "cloud" to "Cloud & Storage",
        "data" to "Databases"
    )

    fun matchesFilter(item: MarketplaceItem): Boolean {
        val matchesSearch = searchQuery.isBlank() ||
            item.name.contains(searchQuery, ignoreCase = true) ||
            item.description.contains(searchQuery, ignoreCase = true) ||
            item.developer.contains(searchQuery, ignoreCase = true) ||
            item.tags.any { it.contains(searchQuery, ignoreCase = true) }

        val matchesCat = when (selectedCategory) {
            "all" -> true
            "cloud" -> item.category in listOf("cloud", "storage")
            else -> item.category.equals(selectedCategory, ignoreCase = true)
        }

        return matchesSearch && matchesCat
    }

    val popularFiltered = remember(searchQuery, selectedCategory, installedIds) {
        MarketplaceRepository.POPULAR_ITEMS.filter { matchesFilter(it) }
    }

    val newFiltered = remember(searchQuery, selectedCategory, installedIds) {
        MarketplaceRepository.NEW_ITEMS.filter { matchesFilter(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090912))
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
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF181824))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "Plugins & Skills Store",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF7C3AED).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${installedIds.size} Installed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA78BFA)
                    )
                }
            }

            // Category Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (id, label) ->
                    val isSelected = selectedCategory == id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = id },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF141420),
                            selectedContainerColor = Color(0xFF7C3AED),
                            labelColor = Color(0xFF9CA3AF),
                            selectedLabelColor = Color.White
                        ),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF7C3AED) else Color(0xFF252538))
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Section 1: Most Popular
            if (popularFiltered.isNotEmpty()) {
                SectionHeader(title = "Featured & Popular")
                popularFiltered.forEach { item ->
                    MarketplaceCardItem(
                        item = item,
                        isInstalled = item.id in installedIds,
                        onCardClick = { detailItem = item },
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

            Spacer(Modifier.height(16.dp))

            // Section 2: New & Noteworthy
            if (newFiltered.isNotEmpty()) {
                SectionHeader(title = "New & Compatible")
                newFiltered.forEach { item ->
                    MarketplaceCardItem(
                        item = item,
                        isInstalled = item.id in installedIds,
                        onCardClick = { detailItem = item },
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

            if (popularFiltered.isEmpty() && newFiltered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No plugins match your filter",
                        color = Color(0xFF6B7280),
                        fontSize = 14.sp
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
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF161622),
                border = BorderStroke(1.dp, Color(0xFF2A2A3E)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search plugins, skills, tools...",
                                color = Color(0xFF6B7280),
                                fontSize = 14.sp
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
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Outlined.Close, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Detail Sheet
    detailItem?.let { item ->
        PluginDetailSheet(
            item = item,
            isInstalled = item.id in installedIds,
            onDismiss = { detailItem = null },
            onInstallToggle = {
                val installed = item.id in installedIds
                MarketplaceRepository.setInstalled(context, item.id, !installed)
                installedIds = if (installed) installedIds - item.id else installedIds + item.id
                detailItem = null
            }
        )
    }

    // Auth / Connect Dialog
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
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun MarketplaceCardItem(
    item: MarketplaceItem,
    isInstalled: Boolean,
    onCardClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131E)),
        border = BorderStroke(1.dp, Color(0xFF222232))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (!item.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(item.iconColor).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = item.iconEmoji, fontSize = 24.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (item.developer.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "· ${item.developer}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.description,
                    fontSize = 12.5.sp,
                    color = Color(0xFF9CA3AF),
                    maxLines = 2
                )
            }

            Spacer(Modifier.width(10.dp))

            // Action Button
            IconButton(
                onClick = onActionClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isInstalled) Color(0xFF10B981).copy(alpha = 0.15f)
                        else Color(0xFF7C3AED).copy(alpha = 0.15f)
                    )
            ) {
                Icon(
                    imageVector = when {
                        isInstalled -> Icons.Outlined.Check
                        item.authType == MarketplaceAuthType.OAUTH -> Icons.Outlined.OpenInNew
                        item.authType != MarketplaceAuthType.NONE -> Icons.Outlined.Lock
                        else -> Icons.Outlined.Add
                    },
                    contentDescription = if (isInstalled) "Installed" else "Install",
                    tint = if (isInstalled) Color(0xFF10B981) else Color(0xFFA78BFA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
