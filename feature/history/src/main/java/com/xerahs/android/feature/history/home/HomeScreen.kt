package com.xerahs.android.feature.history.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.EmptyState
import com.xerahs.android.core.ui.FileTypeTile
import com.xerahs.android.core.ui.lumen.AccentArt
import com.xerahs.android.core.ui.lumen.AccentGlow
import com.xerahs.android.core.ui.lumen.BezelCard
import com.xerahs.android.core.ui.lumen.BrandMark
import com.xerahs.android.core.ui.lumen.CaptureCorners
import com.xerahs.android.core.ui.lumen.CircleIconButton
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.HostChip
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.hostColor
import com.xerahs.android.core.ui.lumen.monoStyle
import com.xerahs.android.core.ui.lumen.relativeTime
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is HomeMessage.Toast -> Toast.makeText(context, msg.text, Toast.LENGTH_SHORT).show()
                is HomeMessage.OpenUrl -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(msg.url))) }
            }
        }
    }

    var searching by rememberSaveable { mutableStateOf(uiState.query.isNotEmpty()) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccentGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 120.dp, y = (-140).dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
            )
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandMark()
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(Icons.Outlined.Settings, "Settings", onSettings)
                }
            }

            item(key = "hero") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Eyebrow("● ${uiState.todayCount} ${if (uiState.todayCount == 1) "upload" else "uploads"} today")
                    Spacer(Modifier.size(8.dp))
                    Text(
                        buildAnnotatedString {
                            append("Capture. Upload.\n")
                            withStyle(SpanStyle(color = Lumen.tokens.ink)) { append("Share in a tap.") }
                        },
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }

            item(key = "bento") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .height(158.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button, onClick = onCreate)
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FileUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Upload",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Any file, any host",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LumenCard(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            radius = 22.dp,
                            onClick = {
                                val turningOff = searching
                                searching = !searching
                                if (turningOff) viewModel.onQueryChange("")
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = Lumen.tokens.ink)
                                Text("Search", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        LumenCard(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            radius = 22.dp,
                            onClick = onStats
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Outlined.BarChart, contentDescription = null, tint = Lumen.tokens.ink)
                                Text("Stats", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }

            if (searching) {
                item(key = "search") {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        placeholder = { Text("Search shares") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }

            val firstSection = uiState.sections.firstOrNull()
            val latest = firstSection?.ids?.firstOrNull()?.let { uiState.itemsById[it] }

            if (latest != null && uiState.query.isEmpty()) {
                item(key = "latest") {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        BezelCard(onClick = { onOpen(latest.id) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(108.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .padding(6.dp)
                            ) {
                                val fileExists = remember(latest.filePath) { File(latest.filePath).exists() }
                                if (latest.isImage && (latest.thumbnailPath != null || fileExists)) {
                                    AsyncImage(
                                        model = latest.thumbnailPath ?: latest.filePath,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                                        AccentArt()
                                    }
                                }
                                CaptureCorners()
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 34.dp, top = 12.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = relativeTime(latest.timestamp),
                                        style = monoStyle(11),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(Modifier.size(12.dp))

                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 10.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = latest.url ?: latest.fileName,
                                        style = monoStyle(14),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    HostChip(latest.uploadDestination.displayName, latest.uploadDestination.hostColor())
                                }
                                val latestUrl = latest.url
                                if (latestUrl != null) {
                                    Spacer(Modifier.size(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Lumen.tokens.tint)
                                            .clickable(role = Role.Button) {
                                                clipboard.setText(AnnotatedString(latestUrl))
                                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy link",
                                            tint = Lumen.tokens.ink
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.sections.isEmpty() && !uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.query.isNotBlank()) {
                            EmptyState(
                                icon = Icons.Outlined.Search,
                                title = "No matches"
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Default.Image,
                                title = "Pick an image to get your first link"
                            )
                        }
                    }
                }
            } else {
                uiState.sections.forEach { section ->
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Eyebrow(section.label)
                        }
                    }
                    items(section.ids, key = { it }) { id ->
                        val item = uiState.itemsById[id]
                        if (item != null) {
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                TimelineRow(
                                    item = item,
                                    onOpen = { onOpen(id) },
                                    canDeleteFromHost = viewModel.canDeleteFromHost(item),
                                    onDeleteFromHost = { viewModel.deleteFromHost(item) }
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
private fun TimelineRow(
    item: HistoryItem,
    onOpen: () -> Unit,
    canDeleteFromHost: Boolean = false,
    onDeleteFromHost: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbModifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
        if (item.isImage) {
            AsyncImage(
                model = item.thumbnailPath ?: item.filePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = thumbModifier
            )
        } else {
            FileTypeTile(item.mimeType, thumbModifier)
        }
        Spacer(Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.url ?: item.fileName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativeTime(item.timestamp),
                style = monoStyle(11),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(8.dp))
        HostChip(item.uploadDestination.displayName, item.uploadDestination.hostColor())
        val clipboard = LocalClipboardManager.current
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions"
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Copy link") },
                    onClick = {
                        clipboard.setText(AnnotatedString(item.url ?: item.filePath))
                        menuOpen = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = { onOpen(); menuOpen = false },
                    leadingIcon = { Icon(Icons.Default.OpenInFull, contentDescription = null) }
                )
                if (canDeleteFromHost) {
                    DropdownMenuItem(
                        text = { Text("Delete from host") },
                        onClick = { menuOpen = false; confirmDelete = true },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from host?") },
            text = {
                Text(
                    if (item.uploadDestination == UploadDestination.CUSTOM_HTTP) {
                        "Opens the host's deletion page."
                    } else {
                        "This removes the file from ${item.uploadDestination.displayName} and from your history."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteFromHost() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
