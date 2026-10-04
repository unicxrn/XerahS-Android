package com.xerahs.android.feature.upload

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.common.toShortDate
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.FileTypeTile
import com.xerahs.android.core.ui.StatusBanner
import com.xerahs.android.core.ui.lumen.BezelCard
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.hostColor
import com.xerahs.android.core.ui.lumen.monoStyle
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isBatch = imagePaths.size > 1

    LaunchedEffect(imagePath, imagePaths) {
        viewModel.setFiles(if (isBatch) imagePaths else listOf(imagePath))
    }
    val mimeType = remember(imagePath) { MimeTypes.fromFileName(imagePath) }
    val isImage = MimeTypes.isRasterImage(mimeType)

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

    if (uiState.pendingPrompts.isNotEmpty()) {
        val answers = remember(uiState.pendingPrompts) {
            mutableStateMapOf<String, String>().apply { uiState.pendingPrompts.forEach { put(it.title, it.default) } }
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelPrompts() },
            title = { Text("Uploader needs input") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.pendingPrompts.forEach { prompt ->
                        OutlinedTextField(
                            value = answers[prompt.title].orEmpty(),
                            onValueChange = { answers[prompt.title] = it },
                            label = { Text(prompt.title.ifBlank { "Value" }) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.submitPromptValues(answers.toMap()) }) { Text("Upload") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelPrompts() }) { Text("Cancel") } }
        )
    }

    // Destination + profile selector sheet
    if (showDestinationSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDestinationSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
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

    LaunchedEffect(uiState.pendingAfterUpload) {
        val event = uiState.pendingAfterUpload ?: return@LaunchedEffect
        viewModel.consumeAfterUpload()
        val first = event.urls.first()
        if (AfterUploadAction.COPY_URL in event.actions) {
            clipboardManager.setText(AnnotatedString(event.urls.joinToString("\n")))
            scope.launch { snackbarHostState.showSnackbar(if (event.urls.size > 1) "Links copied" else "Link copied") }
        }
        val shared = AfterUploadAction.SHARE_SHEET in event.actions
        if (shared) {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, first), null)
            )
        }
        // Don't also launch the browser if we just launched the share sheet -- that
        // would pop two activities on top of the upload screen for one event.
        if (!shared && AfterUploadAction.OPEN_URL in event.actions && first.startsWith("http", ignoreCase = true)) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(first))) }
        }
    }

    // Display-only: remembers whether the link was copied to the clipboard by the effect above,
    // so the success headline below can say "Link copied." even after pendingAfterUpload is consumed.
    var linkWasCopied by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.pendingAfterUpload) {
        uiState.pendingAfterUpload?.let { event ->
            if (AfterUploadAction.COPY_URL in event.actions) linkWasCopied = true
        }
    }

    val bitmap = remember(imagePath) {
        if (isImage) BitmapFactory.decodeFile(imagePath) else null
    }
    val dimensions = remember(imagePath) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}x${opts.outHeight}" else null
    }

    val isSuccess = uiState.result?.success == true
    val isError = uiState.result != null && uiState.result?.success == false

    // Once the upload has succeeded, Back finishes the flow (returns to Home) rather than
    // stepping back into the editor. Before that, Back behaves normally so the user can re-edit.
    BackHandler(enabled = isSuccess) { onUploadComplete() }

    Scaffold(
        topBar = {
            LumenTopBar(
                title = if (isBatch) "Upload ${imagePaths.size} files" else "Upload",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- File header: thumbnail, name, mime/size metadata ----
            val fileName = if (isBatch) "${imagePaths.size} files selected" else java.io.File(imagePath).name
            val metadata = remember(imagePath, imagePaths) {
                if (isBatch) {
                    val totalBytes = imagePaths.sumOf { java.io.File(it).length() }
                    listOfNotNull(mimeType, formatFileSize(totalBytes)).joinToString("  ·  ")
                } else {
                    val file = java.io.File(imagePath)
                    val sizeText = if (file.exists()) formatFileSize(file.length()) else null
                    listOfNotNull(mimeType, sizeText, dimensions).joinToString("  ·  ")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Selected file",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        FileTypeTile(mimeType, Modifier.fillMaxSize(), iconSize = 28.dp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metadata,
                        style = monoStyle(11),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isUploading) {
                UploadProgressRow(uiState = uiState, isBatch = isBatch)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ---- Result / error feedback region ----
            if (isSuccess) {
                UploadSuccessBadge(linkCopied = linkWasCopied)
                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(16.dp))
                PillCta(text = "Done", onClick = onUploadComplete, icon = Icons.Default.Check)
            } else if (isError) {
                StatusBanner(
                    icon = Icons.Default.Error,
                    title = "Upload failed",
                    subtitle = uiState.result?.errorMessage,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                uiState.result?.errorMessage?.let { message ->
                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(message)) }) {
                        Text("Copy error details")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                PillCta(
                    text = "Retry",
                    onClick = { if (isBatch) viewModel.uploadBatch(imagePaths) else viewModel.upload(imagePath) },
                    icon = Icons.Default.Refresh
                )
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
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    LumenFilterChip(
                                        selected = uiState.selectedAlbumId == null,
                                        onClick = { viewModel.selectAlbum(null) },
                                        label = "None"
                                    )
                                    uiState.albums.forEach { album ->
                                        LumenFilterChip(
                                            selected = uiState.selectedAlbumId == album.id,
                                            onClick = { viewModel.selectAlbum(album.id) },
                                            label = album.name
                                        )
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
                                        LumenFilterChip(
                                            selected = tag.id in uiState.selectedTagIds,
                                            onClick = { viewModel.toggleTag(tag.id) },
                                            label = tag.name
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ---- "Upload to" card ----
                val activeProfile = uiState.profiles.find { it.id == uiState.selectedProfileId }
                val destLabel = activeProfile?.name ?: uiState.selectedDestination.displayName

                Eyebrow("Upload to")
                Spacer(modifier = Modifier.height(6.dp))
                LumenCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 5.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable(enabled = !uiState.isUploading) { showDestinationSheet = true }
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(uiState.selectedDestination.hostColor().copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(uiState.selectedDestination.hostColor())
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = destLabel,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.selectedDestination.displayName,
                                style = monoStyle(11),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showDestinationSheet = true },
                            enabled = !uiState.isUploading
                        ) {
                            Text("Change")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---- Primary action ----
                val actionLabel = if (uiState.isUploading) {
                    "Uploading…"
                } else if (isBatch) {
                    "Upload ${imagePaths.size} to ${uiState.selectedDestination.displayName}"
                } else {
                    "Upload to $destLabel"
                }
                PillCta(
                    text = actionLabel,
                    onClick = { if (isBatch) viewModel.uploadBatch(imagePaths) else viewModel.upload(imagePath) },
                    icon = Icons.Default.NorthEast,
                    loading = uiState.isUploading,
                    enabled = !uiState.isUploading
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LumenFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Lumen.tokens.tint,
            selectedLabelColor = Lumen.tokens.ink
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Lumen.tokens.hairline,
            borderWidth = 1.dp
        )
    )
}

/** The "Uploaded." moment: a tinted check badge with a headline, matching ShareCard's success pattern. */
@Composable
private fun UploadSuccessBadge(linkCopied: Boolean) {
    Column {
        Box(
            modifier = Modifier
                .size(74.dp)
                .background(Lumen.tokens.tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            buildAnnotatedString {
                append("Uploaded.\n")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(if (linkCopied) "Link copied." else "Link ready.")
                }
            },
            style = MaterialTheme.typography.displaySmall
        )
    }
}

@Composable
private fun UploadProgressRow(uiState: UploadUiState, isBatch: Boolean) {
    LumenCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            text = if (isBatch && uiState.batchProgress != null) {
                "Uploading ${uiState.batchProgress.first}/${uiState.batchProgress.second}…"
            } else "Uploading…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (uiState.uploadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { uiState.uploadProgress },
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    trackColor = Lumen.tokens.tint,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "${(uiState.uploadProgress * 100).toInt()}%", style = monoStyle(12))
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    trackColor = Lumen.tokens.tint,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun UrlResultCard(url: String, onCopy: () -> Unit) {
    BezelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = url,
                modifier = Modifier.weight(1f),
                style = monoStyle(16),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Lumen.tokens.tint)
                    .clickable(role = Role.Button, onClick = onCopy),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy URL",
                    tint = Lumen.tokens.ink,
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

        uiState.selectableDestinations.forEach { dest ->
            val isSelected = uiState.selectedProfileId == null && uiState.selectedDestination == dest
            DestinationRow(
                title = dest.displayName,
                subtitle = "Default",
                color = dest.hostColor(),
                selected = isSelected,
                onClick = {
                    onSelectDestination(dest)
                    onDone()
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Profiles for the currently selected destination
        val destProfiles = uiState.allowedProfiles(uiState.selectedDestination)
        if (destProfiles.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Lumen.tokens.hairline)
            Text(
                text = "${uiState.selectedDestination.displayName} profiles",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            destProfiles.forEach { profile ->
                val isSelected = uiState.selectedProfileId == profile.id
                DestinationRow(
                    title = profile.name,
                    subtitle = profile.destination.displayName,
                    color = profile.destination.hostColor(),
                    selected = isSelected,
                    onClick = {
                        onSelectProfile(profile.id)
                        onDone()
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DestinationRow(
    title: String,
    subtitle: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Lumen.tokens.tint else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = subtitle, style = monoStyle(11), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
