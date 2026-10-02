package com.xerahs.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SettingsGroupCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                viewModel.exportBackup(outputStream)
            } else {
                viewModel.cancelPassphrase()
                viewModel.reportExportError("Couldn't open the file")
            }
        } else {
            viewModel.cancelPassphrase()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.previewImport(inputStream)
            }
        }
    }

    LaunchedEffect(uiState.exportImportMessage) {
        uiState.exportImportMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    uiState.backupPassphraseRequest?.let { request ->
        var first by remember(request) { mutableStateOf("") }
        var second by remember(request) { mutableStateOf("") }
        val exporting = request == BackupPassphraseRequest.EXPORT
        val error = when {
            exporting && first.length in 1..7 -> "Use at least 8 characters"
            exporting && second.isNotEmpty() && first != second -> "Passphrases don't match"
            else -> null
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelPassphrase() },
            title = { Text(if (exporting) "Protect your backup" else "Unlock backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (exporting) Text("The backup includes passwords and keys. You need this passphrase to restore it.")
                    OutlinedTextField(first, { first = it }, label = { Text("Passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (exporting) OutlinedTextField(second, { second = it }, label = { Text("Repeat passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), isError = error != null,
                        supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = if (exporting) first.length >= 8 && first == second else first.isNotEmpty(),
                    onClick = {
                        if (exporting) {
                            viewModel.setExportPassphrase(first.toCharArray())
                            exportLauncher.launch("xerahs-backup-${java.time.LocalDate.now()}.xsbk")
                        } else {
                            viewModel.unlockBackup(first.toCharArray())
                        }
                    }
                ) { Text(if (exporting) "Continue" else "Unlock") }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelPassphrase() }) { Text("Cancel") } }
        )
    }

    // Show conflict resolution screen when import preview is available
    if (uiState.importPreview != null) {
        ConflictResolutionScreen(
            preview = uiState.importPreview!!,
            onApply = { viewModel.applyResolvedImport() },
            onCancel = { viewModel.cancelImportPreview() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup") },
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
        ) {
            SettingsGroupCard(modifier = Modifier.padding(horizontal = 0.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Export a passphrase-protected backup or import from a previous backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Warning banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Exported files contain upload credentials and API keys. Keep them secure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.requestExportPassphrase() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export encrypted backup")
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
    }
}
