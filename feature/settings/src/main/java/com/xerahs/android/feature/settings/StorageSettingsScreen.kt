package com.xerahs.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.formatSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    val capturesDir = remember(refreshKey) { File(context.filesDir, "captures") }
    val thumbnailsDir = remember(refreshKey) { File(context.filesDir, "thumbnails") }
    val exportsDir = remember(refreshKey) { File(context.filesDir, "exports") }
    val cacheDir = remember(refreshKey) { context.cacheDir }

    val capturesSize = remember(refreshKey) { dirSize(capturesDir) }
    val capturesCount = remember(refreshKey) { fileCount(capturesDir) }
    val thumbnailsSize = remember(refreshKey) { dirSize(thumbnailsDir) }
    val thumbnailsCount = remember(refreshKey) { fileCount(thumbnailsDir) }
    val exportsSize = remember(refreshKey) { dirSize(exportsDir) }
    val exportsCount = remember(refreshKey) { fileCount(exportsDir) }
    val cacheSize = remember(refreshKey) { dirSize(cacheDir) }

    var showClearConfirm by remember { mutableStateOf<String?>(null) }

    showClearConfirm?.let { section ->
        AlertDialog(
            onDismissRequest = { showClearConfirm = null },
            title = { Text("Clear $section?") },
            text = { Text("This will permanently delete all files in $section. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (section) {
                            "Captures" -> clearDir(capturesDir)
                            "Thumbnails" -> clearDir(thumbnailsDir)
                            "Exports" -> clearDir(exportsDir)
                            "Cache" -> clearDir(cacheDir)
                            "All Storage" -> {
                                clearDir(capturesDir)
                                clearDir(thumbnailsDir)
                                clearDir(exportsDir)
                                clearDir(cacheDir)
                            }
                        }
                        showClearConfirm = null
                        refreshKey++
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StorageSection(
                icon = Icons.Default.Image,
                title = "Captures",
                fileCount = capturesCount,
                totalSize = capturesSize,
                onClear = { showClearConfirm = "Captures" }
            )

            StorageSection(
                icon = Icons.Default.Photo,
                title = "Thumbnails",
                fileCount = thumbnailsCount,
                totalSize = thumbnailsSize,
                onClear = { showClearConfirm = "Thumbnails" }
            )

            StorageSection(
                icon = Icons.Default.Folder,
                title = "Exports",
                fileCount = exportsCount,
                totalSize = exportsSize,
                onClear = { showClearConfirm = "Exports" }
            )

            StorageSection(
                icon = Icons.Default.Cached,
                title = "Cache",
                fileCount = null,
                totalSize = cacheSize,
                onClear = { showClearConfirm = "Cache" }
            )

            Spacer(modifier = Modifier.height(8.dp))

            val totalSize = capturesSize + thumbnailsSize + exportsSize + cacheSize
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Total Storage",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = totalSize.formatSize(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showClearConfirm = "All Storage" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All")
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSection(
    icon: ImageVector,
    title: String,
    fileCount: Int?,
    totalSize: Long,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = buildString {
                        if (fileCount != null) append("$fileCount files - ")
                        append(totalSize.formatSize())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onClear,
                enabled = totalSize > 0
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear $title",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun dirSize(dir: File): Long {
    if (!dir.exists()) return 0L
    return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun fileCount(dir: File): Int {
    if (!dir.exists()) return 0
    return dir.listFiles()?.count { it.isFile } ?: 0
}

private fun clearDir(dir: File) {
    if (!dir.exists()) return
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }
}
