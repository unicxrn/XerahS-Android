package com.xerahs.android.feature.upload

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.AlertDialog
import com.xerahs.android.core.common.toShortDate
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.GradientBorderCard
import com.xerahs.android.core.ui.StatusBanner

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(
    imagePath: String,
    imagePaths: List<String> = emptyList(),
    onUploadComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: UploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val isBatch = imagePaths.size > 1

    var showDestinationSheet by remember { mutableStateOf(false) }
    var albumTagExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Duplicate detection dialog (VM hooks unchanged)
    uiState.duplicateInfo?.let { dupInfo ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicate() },
            icon = {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Already uploaded") },
            text = {
                Column {
                    Text(
                        "This image was uploaded before. Copy the existing link, or upload again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    dupInfo.fileName?.let {
                        Text("File: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    if (dupInfo.timestamp > 0L) {
                        Text(
                            "Uploaded: ${dupInfo.timestamp.toShortDate()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    dupInfo.url?.let { url ->
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.uploadAnyway() }) {
                    Text("Upload anyway")
                }
            },
            dismissButton = {
                val existing = dupInfo.url
                if (existing != null) {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(existing))
                        viewModel.dismissDuplicate()
                    }) {
                        Text("Copy existing")
                    }
                } else {
                    TextButton(onClick = { viewModel.dismissDuplicate() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Destination + profile selector sheet
    if (showDestinationSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDestinationSheet = false },
            sheetState = sheetState
        ) {
            DestinationSheetContent(
                uiState = uiState,
                onSelectDestination = { dest ->
                    viewModel.selectProfile(null)
                    viewModel.selectDestination(dest)
                },
                onSelectProfile = { profileId ->
                    viewModel.selectProfile(profileId)
                },
                onDone = { showDestinationSheet = false }
            )
        }
    }

    // Auto-copy URL for single uploads
    LaunchedEffect(uiState.autoCopiableUrl) {
        uiState.autoCopiableUrl?.let { url ->
            clipboardManager.setText(AnnotatedString(url))
            snackbarHostState.showSnackbar("URL copied to clipboard")
        }
    }

    val bitmap = remember(imagePath) {
        BitmapFactory.decodeFile(imagePath)
    }
    val dimensions = remember(imagePath) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}x${opts.outHeight}" else null
    }

    val isSuccess = uiState.result?.success == true
    val isError = uiState.result != null && uiState.result?.success == false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- Preview card: also the live upload region ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                if (isBatch) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${imagePaths.size} images selected",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(imagePaths) { path ->
                                val thumb = remember(path) { BitmapFactory.decodeFile(path) }
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                ) {
                                    thumb?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = "Selected image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                        // Batch progress overlay strip
                        if (uiState.isUploading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            UploadProgressRow(uiState = uiState, isBatch = true)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 340.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Selected image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Monospace size / dimension caption
                        val file = remember(imagePath) { java.io.File(imagePath) }
                        if (file.exists()) {
                            val parts = buildList {
                                add(formatFileSize(file.length()))
                                dimensions?.let { add(it) }
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                            ) {
                                Text(
                                    text = parts.joinToString("  "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Determinate progress overlaid on the image
                        if (uiState.isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uiState.uploadProgress > 0f) {
                                        CircularProgressIndicator(
                                            progress = { uiState.uploadProgress },
                                            modifier = Modifier.size(72.dp),
                                            strokeWidth = 4.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "${(uiState.uploadProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(72.dp),
                                            strokeWidth = 4.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Result / error feedback region ----
            if (isSuccess) {
                val checkScale = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    checkScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 200f)
                    )
                }
                StatusBanner(
                    icon = Icons.Default.CheckCircle,
                    title = "Upload successful",
                    subtitle = "Uploaded to ${uiState.selectedDestination.displayName}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.scale(checkScale.value)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.batchUrls.size > 1) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${uiState.batchUrls.size} URLs",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        uiState.batchUrls.forEach { batchUrl ->
                            UrlResultCard(url = batchUrl) {
                                clipboardManager.setText(AnnotatedString(batchUrl))
                            }
                        }
                    }
                } else {
                    uiState.result?.url?.let { url ->
                        UrlResultCard(url = url) {
                            clipboardManager.setText(AnnotatedString(url))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                ElevatedButton(
                    onClick = onUploadComplete,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("View history")
                }
            } else if (isError) {
                StatusBanner(
                    icon = Icons.Default.Error,
                    title = "Upload failed",
                    subtitle = uiState.result?.errorMessage,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (isBatch) viewModel.uploadBatch(imagePaths) else viewModel.upload(imagePath)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            } else {
                // ---- Optional album / tag assignment (collapsed) ----
                if (uiState.albums.isNotEmpty() || uiState.tags.isNotEmpty()) {
                    val albumName = uiState.albums.find { it.id == uiState.selectedAlbumId }?.name
                    val tagCount = uiState.selectedTagIds.size
                    val summary = when {
                        albumName != null && tagCount > 0 -> "$albumName · $tagCount tag${if (tagCount > 1) "s" else ""}"
                        albumName != null -> albumName
                        tagCount > 0 -> "$tagCount tag${if (tagCount > 1) "s" else ""}"
                        else -> "Add to album or tags"
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { albumTagExpanded = !albumTagExpanded },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                if (albumTagExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (albumTagExpanded) "Collapse" else "Expand"
                            )
                        }
                    }

                    AnimatedVisibility(visible = albumTagExpanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            if (uiState.albums.isNotEmpty()) {
                                Text(
                                    text = "Album",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                )
                                Box {
                                    var showAlbumMenu by remember { mutableStateOf(false) }
                                    val selectedAlbumName = uiState.albums.find { it.id == uiState.selectedAlbumId }?.name ?: "None"
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable { showAlbumMenu = true },
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Text(
                                            text = selectedAlbumName,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showAlbumMenu,
                                        onDismissRequest = { showAlbumMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("None") },
                                            onClick = {
                                                viewModel.selectAlbum(null)
                                                showAlbumMenu = false
                                            }
                                        )
                                        uiState.albums.forEach { album ->
                                            DropdownMenuItem(
                                                text = { Text(album.name) },
                                                onClick = {
                                                    viewModel.selectAlbum(album.id)
                                                    showAlbumMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.tags.isNotEmpty()) {
                                Text(
                                    text = "Tags",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    uiState.tags.forEach { tag ->
                                        FilterChip(
                                            selected = tag.id in uiState.selectedTagIds,
                                            onClick = { viewModel.toggleTag(tag.id) },
                                            label = { Text(tag.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ---- Destination summary + Change affordance ----
                val activeProfile = uiState.profiles.find { it.id == uiState.selectedProfileId }
                val destLabel = activeProfile?.name ?: uiState.selectedDestination.displayName

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !uiState.isUploading) { showDestinationSheet = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = destinationIcon(uiState.selectedDestination),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Destination",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = destLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { showDestinationSheet = true },
                            enabled = !uiState.isUploading
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change destination",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Morphing primary action (thumb zone) ----
                val actionLabel = if (uiState.isUploading) {
                    "Uploading…"
                } else if (isBatch) {
                    "Upload ${imagePaths.size} to ${uiState.selectedDestination.displayName}"
                } else {
                    "Upload to $destLabel"
                }
                Button(
                    onClick = {
                        if (isBatch) viewModel.uploadBatch(imagePaths) else viewModel.upload(imagePath)
                    },
                    enabled = !uiState.isUploading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (uiState.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UploadProgressRow(uiState: UploadUiState, isBatch: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isBatch && uiState.batchProgress != null) {
                    "Uploading ${uiState.batchProgress.first}/${uiState.batchProgress.second}…"
                } else "Uploading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UrlResultCard(url: String, onCopy: () -> Unit) {
    GradientBorderCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = url,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FilledTonalIconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy URL",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationSheetContent(
    uiState: UploadUiState,
    onSelectDestination: (UploadDestination) -> Unit,
    onSelectProfile: (String?) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Choose destination",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        UploadDestination.entries.forEach { dest ->
            val isSelected = uiState.selectedProfileId == null && uiState.selectedDestination == dest
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable {
                        onSelectDestination(dest)
                        onDone()
                    },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = destinationIcon(dest),
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = dest.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Profiles for the currently selected destination
        val destProfiles = uiState.profiles.filter { it.destination == uiState.selectedDestination }
        if (destProfiles.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = "${uiState.selectedDestination.displayName} profiles",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            destProfiles.forEach { profile ->
                val isSelected = uiState.selectedProfileId == profile.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable {
                            onSelectProfile(profile.id)
                            onDone()
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun destinationIcon(destination: UploadDestination): ImageVector {
    return when (destination) {
        UploadDestination.IMGUR -> Icons.Default.Cloud
        UploadDestination.S3 -> Icons.Default.Storage
        UploadDestination.FTP -> Icons.Default.Dns
        UploadDestination.SFTP -> Icons.Default.Security
        UploadDestination.CUSTOM_HTTP -> Icons.Default.Http
        UploadDestination.LOCAL -> Icons.Default.CloudUpload
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
