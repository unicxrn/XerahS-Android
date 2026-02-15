package com.xerahs.android.feature.s3explorer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xerahs.android.core.common.Result
import com.xerahs.android.core.common.formatSize
import com.xerahs.android.core.common.toShortDate
import com.xerahs.android.core.ui.AnimatedListItem
import com.xerahs.android.core.ui.EmptyState
import com.xerahs.android.core.ui.ShimmerBox
import com.xerahs.android.core.ui.StatCard
import com.xerahs.android.feature.s3explorer.model.S3Folder
import com.xerahs.android.feature.s3explorer.model.S3Object
import com.xerahs.android.feature.s3explorer.model.SortDirection
import com.xerahs.android.feature.s3explorer.model.SortField
import com.xerahs.android.feature.s3explorer.model.SortOption
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sort
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun S3ExplorerScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: S3ExplorerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteKey by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuObj by remember { mutableStateOf<S3Object?>(null) }

    // Image preview dialog
    uiState.previewObject?.let { obj ->
        ImagePreviewDialog(
            obj = obj,
            viewModel = viewModel,
            onDismiss = { viewModel.setPreviewObject(null) },
            onDelete = {
                pendingDeleteKey = obj.key
                showDeleteConfirm = true
            },
            onDownload = {
                scope.launch {
                    downloadToDevice(context, viewModel, obj)
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            itemCount = pendingDeleteKey?.let { 1 } ?: uiState.selectedObjects.size,
            onConfirm = {
                pendingDeleteKey?.let { key ->
                    viewModel.deleteSingle(key)
                } ?: viewModel.deleteSelected()
                pendingDeleteKey = null
                showDeleteConfirm = false
            },
            onDismiss = {
                pendingDeleteKey = null
                showDeleteConfirm = false
            }
        )
    }

    // Create folder dialog
    if (uiState.showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.setShowCreateFolderDialog(false) },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (folderName.isNotBlank()) viewModel.createFolder(folderName) },
                    enabled = folderName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCreateFolderDialog(false) }) { Text("Cancel") }
            }
        )
    }

    // Rename dialog
    uiState.showRenameDialog?.let { obj ->
        var newName by remember { mutableStateOf(obj.name) }
        AlertDialog(
            onDismissRequest = { viewModel.setShowRenameDialog(null) },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (newName.isNotBlank()) viewModel.renameObject(obj, newName) },
                    enabled = newName.isNotBlank() && newName != obj.name
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowRenameDialog(null) }) { Text("Cancel") }
            }
        )
    }

    // Move dialog
    uiState.showMoveDialog?.let { obj ->
        var destPrefix by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.setShowMoveDialog(null) },
            title = { Text("Move ${obj.name}") },
            text = {
                OutlinedTextField(
                    value = destPrefix,
                    onValueChange = { destPrefix = it },
                    label = { Text("Destination prefix") },
                    supportingText = { Text("e.g. photos/2024/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (destPrefix.isNotBlank()) viewModel.moveObject(obj, destPrefix) },
                    enabled = destPrefix.isNotBlank()
                ) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowMoveDialog(null) }) { Text("Cancel") }
            }
        )
    }

    // Context menu for file items
    contextMenuObj?.let { obj ->
        AlertDialog(
            onDismissRequest = { contextMenuObj = null },
            title = { Text(obj.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        contextMenuObj = null
                        viewModel.setShowRenameDialog(obj)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rename")
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = {
                        contextMenuObj = null
                        viewModel.setShowMoveDialog(obj)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Move")
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = {
                        contextMenuObj = null
                        pendingDeleteKey = obj.key
                        showDeleteConfirm = true
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = {
                        val obj2 = contextMenuObj
                        contextMenuObj = null
                        obj2?.let {
                            scope.launch { downloadToDevice(context, viewModel, it) }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuObj = null }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("S3 Explorer") },
                actions = {
                    if (uiState.isConfigured) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortField.entries.forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text(field.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            val current = uiState.sortOption
                                            val newDir = if (current.field == field) {
                                                if (current.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
                                            } else SortDirection.ASC
                                            viewModel.setSortOption(SortOption(field, newDir))
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (uiState.sortOption.field == field) {
                                                Text(
                                                    if (uiState.sortOption.direction == SortDirection.ASC) "↑" else "↓",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.setShowCreateFolderDialog(true) }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Create folder")
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView
                                else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "Toggle view"
                            )
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isConfigured && !uiState.isLoading) {
                // Not configured state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            icon = Icons.Default.CloudOff,
                            title = "S3 not configured",
                            subtitle = "Set up your S3 credentials in Settings to browse your bucket"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = onNavigateToSettings) {
                            Text("Configure S3")
                        }
                    }
                }
            } else if (uiState.isConfigured) {
                // Search bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search files...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )

                // Stats row
                if (!uiState.isLoading && uiState.objects.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            label = "Files",
                            value = uiState.totalFiles.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Total Size",
                            value = uiState.totalSize.formatSize(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Breadcrumb bar
                BreadcrumbBar(
                    pathSegments = uiState.pathSegments,
                    onNavigateToRoot = { viewModel.navigateToRoot() },
                    onNavigateToBreadcrumb = { viewModel.navigateToBreadcrumb(it) }
                )

                // Selection bar
                if (uiState.selectedObjects.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.selectedObjects.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Clear")
                            }
                            FilledTonalButton(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }

                // Content
                when {
                    uiState.isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp)
                        ) {
                            repeat(5) {
                                ShimmerBox(height = 64.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    uiState.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EmptyState(
                                    icon = Icons.Default.CloudOff,
                                    title = "Error loading bucket",
                                    subtitle = uiState.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(onClick = { viewModel.refresh() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    uiState.filteredFolders.isEmpty() && uiState.filteredObjects.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                icon = Icons.Default.CloudQueue,
                                title = if (uiState.searchQuery.isNotEmpty()) "No matches" else "Empty",
                                subtitle = if (uiState.searchQuery.isNotEmpty()) "Try a different search term"
                                else "This location has no files or folders"
                            )
                        }
                    }
                    uiState.viewMode == ViewMode.LIST -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
                        ) {
                            itemsIndexed(
                                uiState.filteredFolders,
                                key = { _, f -> "folder_${f.prefix}" }
                            ) { index, folder ->
                                AnimatedListItem(index = index) {
                                    FolderListItem(
                                        folder = folder,
                                        onClick = { viewModel.navigateToFolder(folder) }
                                    )
                                }
                            }
                            itemsIndexed(
                                uiState.filteredObjects,
                                key = { _, o -> "obj_${o.key}" }
                            ) { index, obj ->
                                AnimatedListItem(index = index + uiState.filteredFolders.size) {
                                    FileListItem(
                                        obj = obj,
                                        isSelected = uiState.selectedObjects.contains(obj.key),
                                        viewModel = viewModel,
                                        onClick = {
                                            if (uiState.selectedObjects.isNotEmpty()) {
                                                viewModel.toggleSelection(obj.key)
                                            } else if (obj.isImage) {
                                                viewModel.setPreviewObject(obj)
                                            }
                                        },
                                        onLongClick = { contextMenuObj = obj }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(
                                start = 8.dp, end = 8.dp,
                                top = 4.dp, bottom = 16.dp
                            )
                        ) {
                            itemsIndexed(
                                uiState.filteredFolders,
                                key = { _, f -> "folder_${f.prefix}" }
                            ) { index, folder ->
                                AnimatedListItem(index = index) {
                                    FolderGridItem(
                                        folder = folder,
                                        onClick = { viewModel.navigateToFolder(folder) }
                                    )
                                }
                            }
                            itemsIndexed(
                                uiState.filteredObjects,
                                key = { _, o -> "obj_${o.key}" }
                            ) { index, obj ->
                                AnimatedListItem(index = index + uiState.filteredFolders.size) {
                                    FileGridItem(
                                        obj = obj,
                                        isSelected = uiState.selectedObjects.contains(obj.key),
                                        viewModel = viewModel,
                                        onClick = {
                                            if (uiState.selectedObjects.isNotEmpty()) {
                                                viewModel.toggleSelection(obj.key)
                                            } else if (obj.isImage) {
                                                viewModel.setPreviewObject(obj)
                                            }
                                        },
                                        onLongClick = { contextMenuObj = obj }
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

@Composable
private fun BreadcrumbBar(
    pathSegments: List<String>,
    onNavigateToRoot: () -> Unit,
    onNavigateToBreadcrumb: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (pathSegments.isEmpty()) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clickable { onNavigateToRoot() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Bucket root",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bucket", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        itemsIndexed(pathSegments) { index, segment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (index == pathSegments.lastIndex) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { onNavigateToBreadcrumb(index) }
                ) {
                    Text(
                        text = segment,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderListItem(
    folder: S3Folder,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FolderGridItem(
    folder: S3Folder,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    obj: S3Object,
    isSelected: Boolean,
    viewModel: S3ExplorerViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.size(40.dp)
                )
            } else if (obj.isImage) {
                val (url, headers) = remember(obj.key) { viewModel.getSignedUrl(obj.key) }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                        .crossfade(true)
                        .size(160)
                        .build(),
                    contentDescription = obj.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = obj.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = obj.size.formatSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (obj.lastModified > 0) {
                        Text(
                            text = obj.lastModified.toShortDate(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    obj: S3Object,
    isSelected: Boolean,
    viewModel: S3ExplorerViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (obj.isImage) {
                val (url, headers) = remember(obj.key) { viewModel.getSignedUrl(obj.key) }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                        .crossfade(true)
                        .size(320)
                        .build(),
                    contentDescription = obj.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = obj.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                )
                Checkbox(
                    checked = true,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // File info at bottom
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = obj.size.formatSize(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    obj: S3Object,
    viewModel: S3ExplorerViewModel,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    val (url, headers) = remember(obj.key) { viewModel.getSignedUrl(obj.key) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                    .crossfade(true)
                    .build(),
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
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentScale = ContentScale.Fit
            )

            // Close button
            FilledTonalIconButton(
                onClick = onDismiss,
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
                    text = obj.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = obj.size.formatSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    if (obj.lastModified > 0) {
                        Text(
                            text = obj.lastModified.toShortDate(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                if (obj.storageClass.isNotEmpty()) {
                    Text(
                        text = obj.storageClass,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                    FilledTonalButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    itemCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${if (itemCount == 1) "file" else "$itemCount files"}?") },
        text = {
            Text(
                if (itemCount == 1) "This file will be permanently deleted from your S3 bucket. This cannot be undone."
                else "These $itemCount files will be permanently deleted from your S3 bucket. This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private suspend fun downloadToDevice(
    context: Context,
    viewModel: S3ExplorerViewModel,
    obj: S3Object
) {
    when (val result = viewModel.downloadObject(obj.key)) {
        is Result.Success -> {
            val bytes = result.data
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, obj.name)
                    put(MediaStore.Downloads.MIME_TYPE, guessMimeType(obj.extension))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(bytes)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, obj.name)
                FileOutputStream(file).use { it.write(bytes) }
            }
            Toast.makeText(context, "Downloaded ${obj.name}", Toast.LENGTH_SHORT).show()
        }
        is Result.Error -> {
            Toast.makeText(
                context,
                "Download failed: ${result.message ?: result.exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        is Result.Loading -> {}
    }
}

private fun guessMimeType(extension: String): String = when (extension) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "json" -> "application/json"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "application/javascript"
    "zip" -> "application/zip"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    else -> "application/octet-stream"
}
