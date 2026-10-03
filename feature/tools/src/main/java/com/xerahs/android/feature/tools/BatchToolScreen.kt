package com.xerahs.android.feature.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.image.Watermark
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchToolScreen(onBack: () -> Unit, onUpload: (List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var options by remember { mutableStateOf(BatchOptions()) }
    var watermarkText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)) { picked ->
        uris = picked
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Resize & convert") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("Choose images")
            }
            if (uris.isNotEmpty()) Text("${uris.size} images selected")

            Text("Max size", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Original", 3840 to "3840", 1920 to "1920", 1280 to "1280", 1080 to "1080").forEach { (dim, label) ->
                    FilterChip(
                        selected = options.maxDimension == dim,
                        onClick = { options = options.copy(maxDimension = dim) },
                        label = { Text(label) }
                    )
                }
            }

            Text("Format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = options.format == fmt,
                        onClick = { options = options.copy(format = fmt) },
                        label = { Text(fmt.label) }
                    )
                }
            }

            if (options.format != OutputFormat.PNG) {
                Text("Quality: ${options.quality}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = options.quality.toFloat(),
                    onValueChange = { options = options.copy(quality = it.toInt()) },
                    valueRange = 40f..100f
                )
            }

            OutlinedTextField(
                value = watermarkText,
                onValueChange = {
                    watermarkText = it
                    options = options.copy(watermark = if (it.isNotBlank()) Watermark(it) else null)
                },
                label = { Text("Watermark text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val outDir = File(context.cacheDir, "tools_batch_${System.currentTimeMillis()}")
                        val done = mutableListOf<File>(); var saved = 0; var failed = 0
                        withContext(Dispatchers.Default) {
                            uris.forEachIndexed { i, uri ->
                                ensureActive()
                                runCatching {
                                    val f = BatchImageProcessor.process(context, uri, options, outDir, i)
                                    val gallerySaved = BatchImageProcessor.saveToGallery(context, f)
                                    f to gallerySaved
                                }.onSuccess { (f, gallerySaved) -> done += f; if (gallerySaved) saved++ }
                                    .onFailure { failed++ }
                            }
                        }
                        results = done; busy = false
                        message = buildString {
                            append("Processed ${done.size}")
                            if (saved > 0) append(", saved $saved to Pictures/XerahS")
                            if (failed > 0) append(", $failed failed")
                        }
                    }
                },
                enabled = uris.isNotEmpty() && !busy
            ) { Text("Process") }

            message?.let { Text(it) }
            if (results.isNotEmpty()) {
                Button(onClick = { onUpload(results.map { it.absolutePath }) }) { Text("Upload") }
            }
        }
    }
}
