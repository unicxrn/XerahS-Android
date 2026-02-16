package com.xerahs.android.feature.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.xerahs.android.core.common.formatSize
import com.xerahs.android.core.common.toDateGroupKey
import com.xerahs.android.core.common.toShortDate
import com.xerahs.android.core.domain.model.Album
import com.xerahs.android.core.domain.model.DateFilter
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.Tag
import com.xerahs.android.core.domain.model.UploadDestination
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.material3.FilledTonalButton
import androidx.core.content.FileProvider
import com.xerahs.android.core.ui.AnimatedListItem
import com.xerahs.android.core.ui.EmptyState
import com.xerahs.android.core.ui.ShimmerBox
import com.xerahs.android.core.ui.StatCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<HistoryItem?>(null) }
    var showManageAlbumsDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var longPressItem by remember { mutableStateOf<HistoryItem?>(null) }
    var showSetAlbumDialog by remember { mutableStateOf(false) }
    var showManageItemTagsDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkAlbumDialog by remember { mutableStateOf(false) }
    var showBulkTagsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Fullscreen image preview dialog with swipe navigation
    previewItem?.let { item ->
        val allItems = uiState.items
        val initialIndex = allItems.indexOf(item).coerceAtLeast(0)

        Dialog(
            onDismissRequest = { previewItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                val pagerState = rememberPagerState(
                    initialPage = initialIndex,
                    pageCount = { allItems.size }
                )
                val currentItem = allItems.getOrNull(pagerState.currentPage) ?: item

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageItem = allItems[page]
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offsetX by remember { mutableFloatStateOf(0f) }
                    var offsetY by remember { mutableFloatStateOf(0f) }

                    AsyncImage(
                        model = pageItem.filePath,
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val fingerCount = event.changes.count { it.pressed }
                                        if (fingerCount >= 2) {
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            offsetX += pan.x
                                            offsetY += pan.y
                                            event.changes.forEach { it.consume() }
                                        } else if (fingerCount == 1 && scale > 1f) {
                                            val pan = event.calculatePan()
                                            offsetX += pan.x
                                            offsetY += pan.y
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (scale < 1.05f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                // Close button
                FilledTonalIconButton(
                    onClick = { previewItem = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }

                // Info overlay at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = currentItem.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = "${currentItem.timestamp.toShortDate()} - ${currentItem.uploadDestination.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    currentItem.url?.let { url ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(url))
                                scope.launch { snackbarHostState.showSnackbar("URL copied") }
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        FilledTonalButton(onClick = {
                            previewItem = null
                            shareHistoryItem(context, currentItem)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear History?") },
            text = { Text("This will permanently delete all upload history. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Manage Albums dialog
    if (showManageAlbumsDialog) {
        ManageAlbumsDialog(
            albums = uiState.albums,
            onCreateAlbum = viewModel::createAlbum,
            onDeleteAlbum = viewModel::deleteAlbum,
            onRenameAlbum = viewModel::renameAlbum,
            onDismiss = { showManageAlbumsDialog = false }
        )
    }

    // Manage Tags dialog
    if (showManageTagsDialog) {
        ManageTagsDialog(
            tags = uiState.tags,
            onCreateTag = viewModel::createTag,
            onDeleteTag = viewModel::deleteTag,
            onDismiss = { showManageTagsDialog = false }
        )
    }

    // Set Album dialog (long-press)
    if (showSetAlbumDialog) {
        longPressItem?.let { item ->
            SetAlbumDialog(
                albums = uiState.albums,
                currentAlbumId = item.albumId,
                onAlbumSelected = { albumId ->
                    viewModel.setItemAlbum(item.id, albumId)
                    showSetAlbumDialog = false
                    longPressItem = null
                },
                onDismiss = {
                    showSetAlbumDialog = false
                    longPressItem = null
                }
            )
        }
    }

    // Manage Item Tags dialog (long-press)
    if (showManageItemTagsDialog) {
        longPressItem?.let { item ->
            ManageItemTagsDialog(
                allTags = uiState.tags,
                itemTags = item.tags,
                onAddTag = { tagId -> viewModel.addTagToItem(item.id, tagId) },
                onRemoveTag = { tagId -> viewModel.removeTagFromItem(item.id, tagId) },
                onDismiss = {
                    showManageItemTagsDialog = false
                    longPressItem = null
                }
            )
        }
    }

    // Bulk delete confirmation
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${uiState.selectedIds.size} items?") },
            text = { Text("This will permanently delete the selected items. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showBulkDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk set album dialog
    if (showBulkAlbumDialog) {
        SetAlbumDialog(
            albums = uiState.albums,
            currentAlbumId = null,
            onAlbumSelected = { albumId ->
                viewModel.setSelectedAlbum(albumId)
                showBulkAlbumDialog = false
            },
            onDismiss = { showBulkAlbumDialog = false }
        )
    }

    // Bulk manage tags dialog
    if (showBulkTagsDialog) {
        ManageItemTagsDialog(
            allTags = uiState.tags,
            itemTags = emptyList(),
            onAddTag = { tagId -> viewModel.addTagToSelected(tagId) },
            onRemoveTag = { tagId -> viewModel.removeTagFromSelected(tagId) },
            onDismiss = { showBulkTagsDialog = false }
        )
    }

    // Long-press context menu
    longPressItem?.let { item ->
        if (!showSetAlbumDialog && !showManageItemTagsDialog) {
            AlertDialog(
                onDismissRequest = { longPressItem = null },
                title = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                showSetAlbumDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Set Album") }
                        TextButton(
                            onClick = {
                                showManageItemTagsDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add/Remove Tags") }
                        TextButton(
                            onClick = {
                                longPressItem = null
                                shareHistoryItem(context, item)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Share") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { longPressItem = null }) { Text("Close") }
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            enabled = uiState.selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = "Deselect All")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("History") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode && uiState.selectedIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalButton(
                            onClick = { showBulkDeleteConfirm = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                        FilledTonalButton(onClick = { showBulkAlbumDialog = true }) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Album")
                        }
                        FilledTonalButton(onClick = { showBulkTagsDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tags")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar with rounded shape
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search history...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            // Stats summary row
            if (!uiState.isLoading && uiState.items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Total Uploads",
                        value = uiState.totalUploads.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Total Size",
                        value = uiState.totalSize.formatSize(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Filter bar
            val activeFilterCount = (if (uiState.filterDestination != null) 1 else 0) +
                    (if (uiState.dateFilter != DateFilter.ALL) 1 else 0) +
                    (if (uiState.filterAlbumId != null) 1 else 0) +
                    uiState.filterTagIds.size

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active filter chips (shown when filters are active)
                if (uiState.filterDestination != null) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text(uiState.filterDestination!!.displayName) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                if (uiState.filterAlbumId != null) {
                    val albumName = uiState.albums.find { it.id == uiState.filterAlbumId }?.name ?: "Album"
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.setAlbumFilter(null) },
                        label = { Text(albumName) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                uiState.filterTagIds.forEach { tagId ->
                    val tagName = uiState.tags.find { it.id == tagId }?.name ?: "Tag"
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.toggleTagFilter(tagId) },
                        label = { Text(tagName) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                if (uiState.dateFilter != DateFilter.ALL) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.setDateFilter(DateFilter.ALL) },
                        label = { Text(uiState.dateFilter.displayName) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Filter button with dropdown
                Box {
                    var showFilterMenu by remember { mutableStateOf(false) }

                    FilterChip(
                        selected = activeFilterCount > 0,
                        onClick = { showFilterMenu = true },
                        label = {
                            Text(if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        // Destination section header
                        Text(
                            text = "Destination",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        DropdownMenuItem(
                            text = { Text("All Destinations") },
                            onClick = {
                                viewModel.setFilter(null)
                                showFilterMenu = false
                            },
                            trailingIcon = {
                                if (uiState.filterDestination == null && uiState.filterAlbumId == null && uiState.filterTagIds.isEmpty()) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                        UploadDestination.entries.forEach { dest ->
                            DropdownMenuItem(
                                text = { Text(dest.displayName) },
                                onClick = {
                                    viewModel.setFilter(dest)
                                    showFilterMenu = false
                                },
                                trailingIcon = {
                                    if (uiState.filterDestination == dest) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Albums section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Album",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = {
                                showFilterMenu = false
                                showManageAlbumsDialog = true
                            }) { Text("Manage", style = MaterialTheme.typography.labelSmall) }
                        }

                        DropdownMenuItem(
                            text = { Text("No Album Filter") },
                            onClick = {
                                viewModel.setAlbumFilter(null)
                                showFilterMenu = false
                            },
                            trailingIcon = {
                                if (uiState.filterAlbumId == null && uiState.filterDestination == null && uiState.filterTagIds.isEmpty()) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                        uiState.albums.forEach { album ->
                            DropdownMenuItem(
                                text = { Text(album.name) },
                                onClick = {
                                    viewModel.setAlbumFilter(album.id)
                                    showFilterMenu = false
                                },
                                trailingIcon = {
                                    if (uiState.filterAlbumId == album.id) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Tags section (multi-select)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tags",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row {
                                if (uiState.filterTagIds.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.clearTagFilter() }) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                TextButton(onClick = {
                                    showFilterMenu = false
                                    showManageTagsDialog = true
                                }) { Text("Manage", style = MaterialTheme.typography.labelSmall) }
                            }
                        }

                        uiState.tags.forEach { tag ->
                            val isSelected = tag.id in uiState.filterTagIds
                            DropdownMenuItem(
                                text = { Text(tag.name) },
                                onClick = {
                                    viewModel.toggleTagFilter(tag.id)
                                    // Don't close dropdown — allow multi-select
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Date section header
                        Text(
                            text = "Date Range",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        DateFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.displayName) },
                                onClick = {
                                    viewModel.setDateFilter(filter)
                                    showFilterMenu = false
                                },
                                trailingIcon = {
                                    if (uiState.dateFilter == filter) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (uiState.isLoading) {
                // Shimmer loading skeleton
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                ) {
                    repeat(3) {
                        ShimmerBox(height = 80.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = "No history yet",
                        subtitle = "Your uploaded images will appear here"
                    )
                }
            } else {
                // Date-grouped list
                val grouped = uiState.items.groupBy { it.timestamp.toDateGroupKey() }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    grouped.forEach { (dateLabel, items) ->
                        stickyHeader {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                            AnimatedListItem(index = index) {
                                if (uiState.isSelectionMode) {
                                    HistoryItemCard(
                                        item = item,
                                        albumName = uiState.albums.find { it.id == item.albumId }?.name,
                                        isSelected = item.id in uiState.selectedIds,
                                        onClick = { viewModel.toggleItemSelection(item.id) },
                                        onLongClick = { viewModel.toggleItemSelection(item.id) },
                                        onCopyUrl = { url ->
                                            clipboardManager.setText(AnnotatedString(url))
                                            scope.launch { snackbarHostState.showSnackbar("URL copied") }
                                        },
                                        onShare = { shareHistoryItem(context, item) }
                                    )
                                } else {
                                    SwipeToDeleteItem(
                                        item = item,
                                        albumName = uiState.albums.find { it.id == item.albumId }?.name,
                                        onClick = { previewItem = item },
                                        onLongClick = {
                                            viewModel.toggleSelectionMode()
                                            viewModel.toggleItemSelection(item.id)
                                        },
                                        onCopyUrl = { url ->
                                            clipboardManager.setText(AnnotatedString(url))
                                            scope.launch {
                                                snackbarHostState.showSnackbar("URL copied")
                                            }
                                        },
                                        onShare = { shareHistoryItem(context, item) },
                                        onDelete = {
                                            viewModel.deleteItem(item.id)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Item deleted",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.undoDelete()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeToDeleteItem(
    item: HistoryItem,
    albumName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                label = "swipe-bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }
    ) {
        HistoryItemCard(
            item = item,
            albumName = albumName,
            onClick = onClick,
            onLongClick = onLongClick,
            onCopyUrl = onCopyUrl,
            onShare = onShare
        )
    }
}

// Destination brand colors
private val ImgurAccent = Color(0xFF1BB76E)
private val S3Accent = Color(0xFFFF9900)
private val FtpAccent = Color(0xFF2196F3)
private val SftpAccent = Color(0xFF607D8B)
private val LocalAccent = Color(0xFF9E9E9E)

private fun destinationAccentColor(destination: UploadDestination): Color {
    return when (destination) {
        UploadDestination.IMGUR -> ImgurAccent
        UploadDestination.S3 -> S3Accent
        UploadDestination.FTP -> FtpAccent
        UploadDestination.SFTP -> SftpAccent
        UploadDestination.CUSTOM_HTTP -> Color(0xFFFF9800)
        UploadDestination.LOCAL -> LocalAccent
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryItemCard(
    item: HistoryItem,
    albumName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onShare: () -> Unit,
    isSelected: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Accent strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(88.dp)
                    .background(destinationAccentColor(item.uploadDestination))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Selection checkbox or thumbnail
                if (isSelected) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = { onClick() }
                        )
                    }
                } else if (item.thumbnailPath != null) {
                    AsyncImage(
                        model = item.thumbnailPath,
                        contentDescription = "Thumbnail",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Destination badge
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = item.uploadDestination.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Album badge
                    if (albumName != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.timestamp.toShortDate(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Tag chips
                    if (item.tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item.tags.forEach { tag ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    item.url?.let { url ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onCopyUrl(url) }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = onShare) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageAlbumsDialog(
    albums: List<Album>,
    onCreateAlbum: (String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newAlbumName by remember { mutableStateOf("") }
    var editingAlbumId by remember { mutableStateOf<String?>(null) }
    var editingAlbumName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Albums") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        placeholder = { Text("New album name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newAlbumName.isNotBlank()) {
                                onCreateAlbum(newAlbumName.trim())
                                newAlbumName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                albums.forEach { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editingAlbumId == album.id) {
                            OutlinedTextField(
                                value = editingAlbumName,
                                onValueChange = { editingAlbumName = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = {
                                    if (editingAlbumName.isNotBlank()) {
                                        onRenameAlbum(album.id, editingAlbumName.trim())
                                        editingAlbumId = null
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Confirm rename",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { editingAlbumId = null }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Text(
                                text = album.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        editingAlbumId = album.id
                                        editingAlbumName = album.name
                                    },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { onDeleteAlbum(album.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                if (albums.isEmpty()) {
                    Text(
                        text = "No albums yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ManageTagsDialog(
    tags: List<Tag>,
    onCreateTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        placeholder = { Text("New tag name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newTagName.isNotBlank()) {
                                onCreateTag(newTagName.trim())
                                newTagName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tag.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { onDeleteTag(tag.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (tags.isEmpty()) {
                    Text(
                        text = "No tags yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun SetAlbumDialog(
    albums: List<Album>,
    currentAlbumId: String?,
    onAlbumSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Album") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onAlbumSelected(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("None", modifier = Modifier.weight(1f))
                        if (currentAlbumId == null) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                albums.forEach { album ->
                    TextButton(
                        onClick = { onAlbumSelected(album.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(album.name, modifier = Modifier.weight(1f))
                            if (currentAlbumId == album.id) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (albums.isEmpty()) {
                    Text(
                        text = "No albums created yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun shareHistoryItem(context: android.content.Context, item: HistoryItem) {
    val shareIntent = if (item.url != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, item.url)
        }
    } else {
        val file = File(item.filePath)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else return
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share"))
}

@Composable
private fun ManageItemTagsDialog(
    allTags: List<Tag>,
    itemTags: List<Tag>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val itemTagIds = itemTags.map { it.id }.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add/Remove Tags") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (allTags.isEmpty()) {
                    Text(
                        text = "No tags created yet. Create tags from the filter menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                allTags.forEach { tag ->
                    val isChecked = tag.id in itemTagIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) onRemoveTag(tag.id) else onAddTag(tag.id)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) onAddTag(tag.id) else onRemoveTag(tag.id)
                            }
                        )
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
