package com.xerahs.android.feature.settings.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.common.sxcu.SxcuPreset
import com.xerahs.android.core.common.sxcu.SxcuPresets
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.ui.SettingsGroupCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploaderImportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: UploaderImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::loadUri)
    }
    var confirmInsecure by remember { mutableStateOf(false) }
    var pickPreset by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf<SxcuPreset?>(null) }

    if (pickPreset) {
        AlertDialog(
            onDismissRequest = { pickPreset = false },
            title = { Text("Add from preset") },
            text = {
                Column {
                    SxcuPresets.all.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(p.description) },
                            modifier = Modifier.clickable { pickPreset = false; preset = p }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickPreset = false }) { Text("Cancel") } }
        )
    }

    preset?.let { p ->
        val answers = remember(p) { mutableStateMapOf<String, String>() }
        AlertDialog(
            onDismissRequest = { preset = null },
            title = { Text(p.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.fields.forEach { f ->
                        OutlinedTextField(
                            value = answers[f.key].orEmpty(),
                            onValueChange = { answers[f.key] = it },
                            label = { Text(f.label) },
                            singleLine = true,
                            visualTransformation = if (f.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = p.fields.all { !answers[it.key].isNullOrBlank() },
                    onClick = { viewModel.loadPreset(p.id, answers.toMap()); preset = null }
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { preset = null }) { Text("Cancel") } }
        )
    }

    if (confirmInsecure) {
        AlertDialog(
            onDismissRequest = { confirmInsecure = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unencrypted connection") },
            text = { Text("At least one selected uploader sends files over plain HTTP. Anyone on the network could read them. Import anyway?") },
            confirmButton = { TextButton(onClick = { confirmInsecure = false; viewModel.import() }) { Text("Import anyway") } },
            dismissButton = { TextButton(onClick = { confirmInsecure = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import uploader") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val s = state) {
                UploaderImportState.Idle -> {
                    Text("Import a ShareX custom uploader (.sxcu) or a XerahS destination config (.xsdc). Each one becomes an upload profile.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose file") }
                        OutlinedButton(onClick = { viewModel.loadText(clipboard.getText()?.text) }) { Text("Paste .sxcu") }
                        OutlinedButton(onClick = { pickPreset = true }) { Text("Add from preset") }
                    }
                }
                UploaderImportState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UploaderImportState.NeedsPassphrase -> {
                    var passphrase by remember { mutableStateOf("") }
                    Text("This .xsdc file is encrypted. Enter the passphrase used when it was exported.")
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = s.error != null,
                        supportingText = s.error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = passphrase.isNotEmpty(),
                        onClick = { viewModel.submitPassphrase(passphrase.toCharArray()); passphrase = "" }
                    ) { Text("Decrypt") }
                }
                is UploaderImportState.Preview -> {
                    s.items.forEachIndexed { index, item ->
                        SettingsGroupCard(modifier = Modifier.padding(horizontal = 0.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = item.selected, onCheckedChange = { viewModel.toggleSelected(index) })
                                    Text(item.draft.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(item.draft.describe(), style = MaterialTheme.typography.bodySmall)
                                item.draft.warnings.forEach { w ->
                                    Text("⚠ $w", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                FilterChip(
                                    selected = item.draft.makeDefault,
                                    onClick = { viewModel.toggleDefault(index) },
                                    label = { Text("Default for ${item.draft.destination.displayName}") }
                                )
                            }
                        }
                    }
                    if (s.skipped.isNotEmpty()) {
                        Text("Skipped: ${s.skipped.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = s.items.any { it.selected },
                        onClick = { if (viewModel.needsInsecureConfirmation()) confirmInsecure = true else viewModel.import() }
                    ) { Text("Import") }
                }
                is UploaderImportState.Done -> {
                    Text(if (s.count == 1) "Imported 1 profile." else "Imported ${s.count} profiles.")
                    Button(onClick = onDone) { Text("Done") }
                }
                is UploaderImportState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::reset) { Text("Try another file") }
                }
            }
        }
    }
}

private fun com.xerahs.android.core.domain.model.ImportDraft.describe(): String = when (val c = config) {
    is UploadConfig.CustomUploaderConfig -> {
        val host = runCatching { java.net.URI(c.spec.requestURL).host }.getOrNull() ?: c.spec.requestURL
        "${c.spec.destinationTypes.joinToString { it.sxcuName }} · ${c.spec.requestMethod} $host · ${c.spec.body.sxcuName}"
    }
    is UploadConfig.S3Config -> "Amazon S3 · bucket ${c.bucket} · ${c.region}"
    else -> destination.displayName
}
