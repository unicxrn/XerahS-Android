package com.xerahs.android.feature.tools

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.FileHasher
import com.xerahs.android.core.common.FileHashes
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.monoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf<String?>(null) }
    var hashes by remember { mutableStateOf<FileHashes?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var compare by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; error = null; hashes = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                    context.contentResolver.openInputStream(uri)!!.use { FileHasher.computeAll(it) }
                }
            }
            busy = false
            result.fold({ hashes = it }, { error = "Couldn't read the file" })
        }
    }

    Scaffold(topBar = { LumenTopBar(title = "Hash checker", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillCta(text = "Choose file", onClick = { picker.launch(arrayOf("*/*")) }, loading = busy)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            fileName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            hashes?.let { h ->
                listOf("MD5" to h.md5, "SHA-1" to h.sha1, "SHA-256" to h.sha256).forEach { (label, value) ->
                    val matched = compare.isNotBlank() && FileHasher.matches(compare, value)
                    LumenCard(contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (matched) "$label (match)" else label, style = MaterialTheme.typography.labelLarge,
                                    color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer { Text(value, style = monoStyle(13)) }
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label")
                            }
                        }
                    }
                }
                OutlinedTextField(compare, { compare = it }, label = { Text("Compare with") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (compare.isNotBlank()) {
                    val any = listOf(h.md5, h.sha1, h.sha256).any { FileHasher.matches(compare, it) }
                    Text(if (any) "Match" else "No match",
                        color = if (any) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
