package com.xerahs.android.feature.upload

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Duplicate detection dialog
    uiState.duplicateInfo?.let { dupInfo ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicate() },
            title = { Text("Duplicate Detected") },
            text = {
                Column {
                    Text("This image was already uploaded.")
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
                            "URL: $url",
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
                    Text("Upload Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicate() }) {
                    Text("Cancel")
                }
            }
        )
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview with flexible height
            if (isBatch) {
                // Batch preview carousel
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
                        Card(
                            modifier = Modifier.size(120.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            thumb?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            } else {
                bitmap?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Box {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 240.dp),
                                contentScale = ContentScale.Fit
                            )
                            // File size badge
                            val file = java.io.File(imagePath)
                            if (file.exists()) {
                                val sizeText = formatFileSize(file.length())
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = sizeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Destination picker - card-based
            Text(
                text = "Upload Destination",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(UploadDestination.entries.toList()) { dest ->
                    val isSelected = uiState.selectedDestination == dest
                    val borderWidth by animateDpAsState(
                        targetValue = if (isSelected) 2.dp else 0.dp,
                        label = "dest-border"
                    )

                    Surface(
                        modifier = Modifier
                            .size(width = 80.dp, height = 88.dp)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        borderWidth,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.large
                                    )
                                } else Modifier
                            )
                            .clickable(
                                role = Role.RadioButton,
                                onClickLabel = "Select ${dest.displayName}"
                            ) { viewModel.selectDestination(dest) },
                        shape = MaterialTheme.shapes.large,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = destinationIcon(dest),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dest.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // Profile selector
            val destProfiles = uiState.profiles.filter { it.destination == uiState.selectedDestination }
            if (destProfiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Box {
                    var showProfileMenu by remember { mutableStateOf(false) }
                    val selectedProfileName = destProfiles.find { it.id == uiState.selectedProfileId }?.name ?: "Default (Global)"

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProfileMenu = true },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = selectedProfileName,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    DropdownMenu(
                        expanded = showProfileMenu,
                        onDismissRequest = { showProfileMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Default (Global)") },
                            onClick = {
                                viewModel.selectProfile(null)
                                showProfileMenu = false
                            }
                        )
                        destProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    viewModel.selectProfile(profile.id)
                                    showProfileMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Album & Tag selection
            if (uiState.albums.isNotEmpty() || uiState.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                // Album dropdown
                if (uiState.albums.isNotEmpty()) {
                    Text(
                        text = "Album",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    Box {
                        var showAlbumMenu by remember { mutableStateOf(false) }
                        val selectedAlbumName = uiState.albums.find { it.id == uiState.selectedAlbumId }?.name ?: "None"

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAlbumMenu = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
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

                // Tag chips
                if (uiState.tags.isNotEmpty()) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            // Upload states
            if (uiState.result != null && uiState.result!!.success) {
                // Success state with animated checkmark
                val checkScale = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    checkScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.4f,
                            stiffness = 200f
                        )
                    )
                }

                StatusBanner(
                    icon = Icons.Default.CheckCircle,
                    title = "Upload Successful!",
                    subtitle = "Uploaded to ${uiState.selectedDestination.displayName}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.scale(checkScale.value)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Show batch URLs if available
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
                            GradientBorderCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = batchUrl,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    FilledTonalIconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(batchUrl))
                                    }) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy URL",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    uiState.result?.url?.let { url ->
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
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                FilledTonalIconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(url))
                                }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy URL",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ElevatedButton(onClick = onUploadComplete) {
                    Text("View History")
                }
            } else if (uiState.isUploading) {
                // Uploading state with pulsing background
                val infiniteTransition = rememberInfiniteTransition(label = "upload-pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse-alpha"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = pulseAlpha)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.uploadProgress > 0f) {
                                CircularProgressIndicator(
                                    progress = { uiState.uploadProgress },
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                            Icon(
                                imageVector = destinationIcon(uiState.selectedDestination),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.selectedDestination.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (uiState.uploadProgress > 0f) {
                            Text(
                                text = "${(uiState.uploadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { uiState.uploadProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = if (isBatch && uiState.batchProgress != null) {
                                    "Uploading ${uiState.batchProgress!!.first}/${uiState.batchProgress!!.second}..."
                                } else {
                                    "Uploading..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else if (uiState.result != null && !uiState.result!!.success) {
                // Error state
                StatusBanner(
                    icon = Icons.Default.Error,
                    title = "Upload Failed",
                    subtitle = uiState.result?.errorMessage,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (isBatch) viewModel.uploadBatch(imagePaths) else viewModel.upload(imagePath)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            } else {
                // Idle state
                Button(
                    onClick = {
                        if (isBatch) {
                            viewModel.uploadBatch(imagePaths)
                        } else {
                            viewModel.upload(imagePath)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBatch) "Upload ${imagePaths.size} Images" else "Upload")
                }
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
